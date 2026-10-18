package parity

import (
	"context"
	"encoding/json"
	"os"
	"strings"
	"testing"
	"time"

	identityquery "github.com/0xsj/mobile-foundry/backend/internal/identity/app/query"
	identitydomain "github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	identitymemory "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/memory"
	identitycap "github.com/0xsj/mobile-foundry/backend/internal/identity/transport/capability/v1"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	consumer "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/capabilities"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func pushCorpus(t *testing.T, name string, into any) {
	t.Helper()
	b, e := os.ReadFile("../../../contracts/fixtures/backend-push/" + name + ".json")
	if e != nil {
		t.Fatal(e)
	}
	if e = json.Unmarshal(b, into); e != nil {
		t.Fatal(e)
	}
}
func pushID(t *testing.T) id.ID {
	t.Helper()
	v, e := (id.Random{}).NewID()
	if e != nil {
		t.Fatal(e)
	}
	return v
}

type parityPushClock struct{ at time.Time }

func (c *parityPushClock) Now() time.Time { return c.at }

type parityEligible func(context.Context, id.ID, id.ID) (bool, error)

func (f parityEligible) SessionEligible(c context.Context, p, s id.ID) (bool, error) {
	return f(c, p, s)
}

type parityGateway func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error)

func (f parityGateway) SendPreview(c context.Context, a app.PushAddress, p id.ID) (app.PushDisposition, error) {
	return f(c, a, p)
}

type parityTargets struct {
	app.PushTargets
	fail bool
}

func (p parityTargets) RetirePushTarget(c context.Context, l app.PushLease, at time.Time) (bool, error) {
	if p.fail {
		return false, apperrors.New(apperrors.Unavailable, "fixture.cleanup", "PRIVATE cleanup failure")
	}
	return p.PushTargets.RetirePushTarget(c, l, at)
}
func TestSharedPushAttempts(t *testing.T) {
	var cases []struct {
		Name             string
		ErrorKind        *string `json:"error_kind"`
		GatewayCalls     int     `json:"gateway_calls"`
		Disposition      *string
		Retired, Enabled bool
		Version          uint64
	}
	pushCorpus(t, "attempts", &cases)
	for _, c := range cases {
		t.Run(c.Name, func(t *testing.T) {
			ctx := context.Background()
			at := time.UnixMicro(1_800_000_000_000_000).UTC()
			clock := &parityPushClock{at}
			store := memory.New()
			actor := app.InstallationActor{Principal: pushID(t), Session: pushID(t), ExpiresAt: at.Add(time.Hour)}
			m := app.InstallationMutation{Actor: actor, ID: pushID(t), RegistrationID: pushID(t), ProofDigest: strings.Repeat("a", 64), Spec: domain.InstallationSpec{Route: domain.PushRoute{Provider: "apns", Application: "dev.example.mobile", Environment: "sandbox"}, Token: "PRIVATE-" + pushID(t).String()}}
			if _, e := store.RegisterInstallation(ctx, m, at); e != nil {
				t.Fatal(e)
			}
			lease := app.PushLease{InstallationID: m.ID, PrincipalID: actor.Principal, SessionID: actor.Session, Version: 1}
			original := lease
			preview := pushID(t)
			calls := 0
			rotate := func(rebind bool) {
				next := m
				next.Expected = 1
				next.RegistrationID = pushID(t)
				next.Spec.Token = "PRIVATE-rotated-" + pushID(t).String()
				if rebind {
					next.Actor.Principal = pushID(t)
					next.Actor.Session = pushID(t)
				}
				if _, e := store.RegisterInstallation(ctx, next, at); e != nil {
					t.Fatal(e)
				}
			}
			sessions := parityEligible(func(context.Context, id.ID, id.ID) (bool, error) {
				switch c.Name {
				case "session_denied":
					return false, nil
				case "session_outage":
					return false, apperrors.New(apperrors.Unavailable, "fixture.outage", "PRIVATE outage")
				case "rotation_during_check":
					rotate(false)
				case "expiry_during_check":
					clock.at = actor.ExpiresAt
				case "revocation_during_check":
					if e := store.RetireSession(ctx, app.SessionRevocation{EventID: pushID(t), PrincipalID: actor.Principal, SessionID: actor.Session, Version: 2, Reason: "sign_out", OccurredAt: at}); e != nil {
						t.Fatal(e)
					}
				}
				return true, nil
			})
			gateway := parityGateway(func(bounded context.Context, a app.PushAddress, p id.ID) (app.PushDisposition, error) {
				calls++
				if _, ok := bounded.Deadline(); !ok {
					t.Fatal("unbounded gateway")
				}
				if a.Lease != original || a.Spec.Token != m.Spec.Token || a.RegisteredAt != at || p != preview {
					t.Fatal("send input changed")
				}
				switch c.Name {
				case "invalid", "cleanup_failure":
					return app.PushInvalidTarget, nil
				case "uncertain":
					return "", apperrors.New(apperrors.Unavailable, "fixture.uncertain", "PRIVATE gateway error")
				case "unknown":
					return "invented", nil
				case "rotation_during_send", "rebind_during_send":
					rotate(c.Name == "rebind_during_send")
					return app.PushInvalidTarget, nil
				case "unregister_during_send":
					if _, e := store.UnregisterInstallation(ctx, actor, m.ID, m.ProofDigest, 1, at); e != nil {
						t.Fatal(e)
					}
					return app.PushInvalidTarget, nil
				}
				return app.PushAccepted, nil
			})
			h := command.SendPushPreview{Targets: parityTargets{store, c.Name == "cleanup_failure"}, Sessions: sessions, Gateway: gateway, Clock: clock}
			switch c.Name {
			case "missing":
				lease.InstallationID = pushID(t)
			case "stale":
				lease.Version = 2
			case "wrong_owner":
				lease.PrincipalID = pushID(t)
			case "wrong_session":
				lease.SessionID = pushID(t)
			case "disabled":
				if _, e := store.UnregisterInstallation(ctx, actor, m.ID, m.ProofDigest, 1, at); e != nil {
					t.Fatal(e)
				}
			case "expired":
				clock.at = actor.ExpiresAt
			case "before_creation":
				clock.at = at.Add(-time.Microsecond)
			}
			callCtx, cancel := context.WithCancel(ctx)
			defer cancel()
			if c.Name == "canceled" {
				cancel()
			}
			got, e := h.Handle(callCtx, lease, preview)
			if (e != nil) != (c.ErrorKind != nil) {
				t.Fatal("attempt error presence differs")
			}
			if e != nil && string(apperrors.KindOf(e)) != *c.ErrorKind {
				t.Fatal("attempt error kind differs")
			}
			if c.Disposition == nil {
				if got.Disposition != "" {
					t.Fatal("unexpected disposition")
				}
			} else if string(got.Disposition) != *c.Disposition {
				t.Fatal("disposition differs")
			}
			if got.Retired != c.Retired || calls != c.GatewayCalls {
				t.Fatal("retirement/call count differs")
			}
			observer := app.InstallationActor{Principal: pushID(t), Session: pushID(t), ExpiresAt: at.Add(time.Hour)}
			current, e := store.GetInstallation(ctx, observer, m.ID, m.ProofDigest, at)
			if e != nil {
				t.Fatal(e)
			}
			v := current.Snapshot()
			if v.Enabled != c.Enabled || v.Version != c.Version || (v.Spec.Token == "") == v.Enabled {
				t.Fatal("retained registry differs")
			}
		})
	}
}

type eligibilityStatus struct{ t *testing.T }

func (s eligibilityStatus) SessionEligible(_ context.Context, p, session id.ID, _ time.Time) (bool, error) {
	s.t.Helper()
	if p.String() != "10000000-0000-4000-8000-000000000001" || session.String() != "10000000-0000-4000-8000-000000000002" {
		s.t.Fatal("provider request changed")
	}
	return true, nil
}

type eligibilityReply string

func (w eligibilityReply) Invoke(_ context.Context, name string, _ any, response any) error {
	if name != consumer.SessionEligibilityCapability {
		return capability.ErrInvalid
	}
	return capability.Decode([]byte(w), response)
}
func TestSharedPushEligibilityWire(t *testing.T) {
	registry := capability.NewRegistry()
	if e := registry.Register(identitycap.ProvideSessionEligibility(identityquery.SessionEligibility{Store: eligibilityStatus{t}, Clock: &parityPushClock{time.UnixMicro(1_800_000_000_000_000).UTC()}})); e != nil {
		t.Fatal(e)
	}
	registry.Freeze()
	var requests []struct {
		Name, Wire, Caller string
		Valid              bool
		ErrorKind          string `json:"error_kind"`
	}
	pushCorpus(t, "requests", &requests)
	for _, c := range requests {
		var response struct {
			Eligible *bool `json:"eligible"`
		}
		e := registry.Client(c.Caller).Invoke(context.Background(), consumer.SessionEligibilityCapability, json.RawMessage(c.Wire), &response)
		if (e == nil) != c.Valid {
			t.Fatal(c.Name, " request admission differs")
		}
		if e != nil && string(apperrors.KindOf(e)) != c.ErrorKind {
			t.Fatal(c.Name, " request failure kind differs")
		}
		if e == nil && (response.Eligible == nil || !*response.Eligible) {
			t.Fatal("answer changed")
		}
	}
	var responses []struct {
		Name, Wire string
		Valid      bool
		Eligible   *bool
	}
	pushCorpus(t, "responses", &responses)
	for _, c := range responses {
		got, e := (consumer.Client{Invoker: eligibilityReply(c.Wire)}).SessionEligible(context.Background(), pushID(t), pushID(t))
		if (e == nil) != c.Valid {
			t.Fatal(c.Name, " response admission differs")
		}
		if e == nil && (c.Eligible == nil || got != *c.Eligible) {
			t.Fatal(c.Name, " response differs")
		}
	}
}
func TestSharedPushSessionStatus(t *testing.T) {
	ctx := context.Background()
	store := identitymemory.New(id.Random{})
	at := time.UnixMicro(1_800_000_000_000_000).UTC()
	p := pushID(t)
	principal, e := identitydomain.NewPrincipal(p, p.String(), "PRIVATE actor", "PRIVATE checkpoint", at)
	if e != nil {
		t.Fatal(e)
	}
	if e = store.Register(ctx, principal, events.Metadata{}); e != nil {
		t.Fatal(e)
	}
	session, e := identitydomain.NewSession(pushID(t), p, strings.Repeat("a", 64), strings.Repeat("b", 64), at)
	if e != nil {
		t.Fatal(e)
	}
	if e = store.Issue(ctx, session, events.Metadata{}); e != nil {
		t.Fatal(e)
	}
	var cases []struct {
		Name           string
		Offset         int64 `json:"offset_micros"`
		Eligible       bool
		WrongPrincipal bool `json:"wrong_principal"`
		MissingSession bool `json:"missing_session"`
	}
	pushCorpus(t, "sessions", &cases)
	for _, c := range cases {
		actor, sid := p, session.Snapshot().ID
		if c.WrongPrincipal {
			actor = pushID(t)
		}
		if c.MissingSession {
			sid = pushID(t)
		}
		got, e := store.SessionEligible(ctx, actor, sid, at.Add(time.Duration(c.Offset)*time.Microsecond))
		if e != nil || got != c.Eligible {
			t.Fatal(c.Name, " session status differs")
		}
	}
}
