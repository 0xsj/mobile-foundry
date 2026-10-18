package parity

import (
	"encoding/json"
	"fmt"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"os"
	"testing"
	"time"
)

func TestSharedJobTransitions(t *testing.T) {
	wire, e := os.ReadFile("../../../contracts/fixtures/backend-push-jobs/transitions.json")
	if e != nil {
		t.Fatal(e)
	}
	var cases []map[string]json.RawMessage
	if json.Unmarshal(wire, &cases) != nil {
		t.Fatal("corpus")
	}
	identity := func(n int) id.ID { return id.ID(fmt.Sprintf("00000000-0000-4000-8000-%012d", n)) }
	at := time.UnixMicro(1800000000000000)
	for _, c := range cases {
		str := func(k, def string) string {
			v, ok := c[k]
			if !ok {
				return def
			}
			var s string
			if json.Unmarshal(v, &s) != nil {
				t.Fatal("fixture string")
			}
			return s
		}
		num := func(k string, def int64) int64 {
			v, ok := c[k]
			if !ok {
				return def
			}
			var n int64
			if json.Unmarshal(v, &n) != nil {
				t.Fatal("fixture number")
			}
			return n
		}
		yes := func(k string) bool { var b bool; _ = json.Unmarshal(c[k], &b); return b }
		t.Run(str("name", ""), func(t *testing.T) {
			status := domain.PushJobStatus(str("state", ""))
			active := status == domain.JobLeased || status == domain.JobSending
			attempts := 1
			code := ""
			switch status {
			case domain.JobQueued, domain.JobExpired, domain.JobCanceled:
				attempts = 0
			case domain.JobFailed:
				attempts = 5
			}
			switch status {
			case domain.JobBlocked:
				code = "target_ineligible"
			case domain.JobFailed:
				code = "attempts_exhausted"
			case domain.JobUncertain:
				code = "provider_unconfirmed"
			case domain.JobExpired:
				code = "expired"
			case domain.JobCanceled:
				code = "user_canceled"
			}
			snapshot := domain.PushJobSnapshot{ID: identity(1), InstallationID: identity(2), PrincipalID: identity(3), SessionID: identity(4), InstallationVersion: 1, Status: status, Attempts: int(num("initial_attempts", int64(attempts))), CreatedAt: at, UpdatedAt: at, ExpiresAt: at.Add(15 * time.Minute), NextAt: at.Add(time.Microsecond * time.Duration(num("next_offset", 0))), Code: code}
			if active {
				snapshot.Claim = identity(5)
				snapshot.LeaseUntil = at.Add(time.Microsecond * time.Duration(num("lease_offset", 30000000)))
			}
			job, e := domain.RestorePushJob(snapshot)
			if e != nil {
				t.Fatal("fixture state", e)
			}
			now := at.Add(time.Microsecond * time.Duration(num("at", 0)))
			token := identity(5)
			if yes("wrong_token") {
				token = identity(6)
			}
			var next domain.PushJob
			switch str("operation", "") {
			case "claim":
				var claimed bool
				next, claimed, e = job.ClaimNext(identity(6), now)
				if e == nil && claimed != yes("claimed") {
					t.Fatal("claim result")
				}
			case "begin":
				next, e = job.Begin(token, now)
			case "finish":
				next, e = job.Finish(token, yes("started"), domain.PushJobStatus(str("status", "")), str("result_code", ""), yes("retired"), now)
			case "cancel":
				next, e = job.Cancel(now)
			case "replace":
				intent := snapshot
				intent.ID = identity(7)
				intent.RetryOf = identity(1)
				intent.DuplicateRiskAccepted = yes("ack")
				var candidate domain.PushJob
				candidate, e = domain.NewPushJob(intent, now)
				if e == nil {
					next, e = job.Replacement(candidate)
				}
			}
			expected := str("expected", "")
			if e != nil {
				if string(apperrors.KindOf(e)) != expected {
					t.Fatal("kind", e, expected)
				}
				return
			}
			v := next.Snapshot()
			if string(v.Status) != expected || v.Attempts != int(num("attempts", 0)) {
				t.Fatal("outcome", v.Status, v.Attempts, expected)
			}
			if _, ok := c["code"]; ok && v.Code != str("code", "") {
				t.Fatal("result code")
			}
			if n := num("next_after", 0); n > 0 && !v.NextAt.Equal(now.Add(time.Duration(n)*time.Microsecond)) {
				t.Fatal("backoff")
			}
			if yes("retired") && !v.Retired {
				t.Fatal("retirement")
			}
		})
	}
}
