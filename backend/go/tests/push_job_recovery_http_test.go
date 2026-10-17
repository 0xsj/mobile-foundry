package tests

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"sync/atomic"
	"testing"
	"time"
)

func TestPushJobRecoveryHTTPProofReplayAndExplicitRisk(t *testing.T) {
	var calls atomic.Int64
	entered, release := make(chan struct{}), make(chan struct{})
	defer close(release)
	h := pushHTTP(t, gatewayFunc(func(ctx context.Context, _ app.PushAddress, _ id.ID) (app.PushDisposition, error) {
		if calls.Add(1) == 1 {
			return "", errors.New("provider acknowledgement unknown")
		}
		close(entered)
		select {
		case <-release:
			return app.PushAccepted, nil
		case <-ctx.Done():
			return "", ctx.Err()
		}
	}), true)
	for _, name := range []string{"recovery-owner", "recovery-foreign"} {
		if status, _, _ := h.request("POST", "/v1/identity/register", "", `{"login":"`+name+`","display_name":"Person","password":"private test passphrase"}`, nil); status != 201 {
			t.Fatal(status)
		}
	}
	a, b := login(t, h, "recovery-owner"), login(t, h, "recovery-foreign")
	proof, _ := installationProof(t)
	wrong, _ := installationProof(t)
	installation, source, child := randomID(t).String(), randomID(t).String(), randomID(t).String()
	create := `{"installation_id":"` + installation + `","registration_id":"` + randomID(t).String() + `","provider":"fcm","application":"dev.example.recovery","environment":"production","token":"private-recovery-token"}`
	if status, _, _ := h.request("POST", "/v1/installations", a.Access, create, map[string]string{"X-Installation-Key": proof}); status != 201 {
		t.Fatal(status)
	}
	base := "/v1/installations/" + installation + "/push-jobs"
	read := map[string]string{"X-Installation-Key": proof}
	write := map[string]string{"X-Installation-Key": proof, "If-Match": `"1"`}
	if status, _, _ := h.request("POST", base, a.Access, `{"job_id":"`+source+`"}`, write); status != 202 {
		t.Fatal(status)
	}
	waitStatus := func(identity, want string) {
		t.Helper()
		deadline := time.Now().Add(5 * time.Second)
		for {
			status, _, wire := h.request("GET", base+"/"+identity, a.Access, "", read)
			var v map[string]any
			_ = json.Unmarshal(wire, &v)
			if status != 200 {
				t.Fatal(status)
			}
			if v["status"] == want {
				return
			}
			if time.Now().After(deadline) {
				t.Fatal("status did not settle", want, string(wire))
			}
			time.Sleep(10 * time.Millisecond)
		}
	}
	waitStatus(source, "uncertain")
	path := base + "/" + source + "/replacement"
	body := `{"job_id":"` + child + `","accept_possible_duplicate":true}`
	if status, _, _ := h.request("GET", path, a.Access, "", read); status != 404 {
		t.Fatal("uncreated replacement", status)
	}
	for _, row := range []struct {
		token, path, body string
		headers           map[string]string
		status            int
	}{
		{"", path, body, write, 401}, {b.Access, path, body, write, 403},
		{a.Access, path, body, map[string]string{"X-Installation-Key": wrong, "If-Match": `"1"`}, 404},
		{a.Access, path, body, read, 428},
		{a.Access, path, `{"job_id":"` + child + `"}`, write, 400},
		{a.Access, path, `{"job_id":"` + child + `","accept_possible_duplicate":null}`, write, 400},
		{a.Access, path, `{"job_id":"` + child + `","accept_possible_duplicate":false}`, write, 409},
		{a.Access, path, `{"job_id":"` + child + `","accept_possible_duplicate":false,"accept_possible_duplicate":true}`, write, 400},
		{a.Access, path, `{"job_id":"` + child + `","ACCEPT_POSSIBLE_DUPLICATE":true}`, write, 400},
		{a.Access, path, `{"job_id":"` + child + `","accept_possible_duplicate":true,"session_id":"foreign"}`, write, 400},
		{a.Access, path + "?retry=true", body, write, 400},
	} {
		status, _, _ := h.request("POST", row.path, row.token, row.body, row.headers)
		if status != row.status || calls.Load() != 1 {
			t.Fatal("recovery guard", status, row.status, calls.Load())
		}
	}
	for range 2 {
		status, hdr, wire := h.request("POST", path, a.Access, body, write)
		var v map[string]any
		if status != 202 || json.Unmarshal(wire, &v) != nil || len(v) != 8 || v["job_id"] != child || hdr.Get("ETag") != "" || hdr.Get("Cache-Control") != "no-store" {
			t.Fatal("replacement projection", status, string(wire))
		}
	}
	select {
	case <-entered:
	case <-time.After(3 * time.Second):
		t.Fatal("replacement not dispatched")
	}
	if status, _, _ := h.request("POST", path, a.Access, `{"job_id":"`+randomID(t).String()+`","accept_possible_duplicate":true}`, write); status != 409 {
		t.Fatal("duplicate replacement", status)
	}
	if status, _, wire := h.request("GET", path, a.Access, "", read); status != 200 || !json.Valid(wire) {
		t.Fatal("replacement readback", status)
	}
	if status, _, _ := h.request("POST", base+"/"+child+"/cancel", a.Access, "", read); status != 409 {
		t.Fatal("sending was canceled", status)
	}
	pending := randomID(t).String()
	if status, _, _ := h.request("POST", base, a.Access, `{"job_id":"`+pending+`"}`, write); status != 202 {
		t.Fatal(status)
	}
	cancelPath := base + "/" + pending + "/cancel"
	for _, row := range []struct {
		token, body string
		headers     map[string]string
		status      int
	}{
		{"", "", read, 401}, {b.Access, "", read, 403}, {a.Access, "", map[string]string{"X-Installation-Key": wrong}, 404},
		{a.Access, `{}`, read, 400}, {a.Access, "", write, 400},
	} {
		if status, _, _ := h.request("POST", cancelPath, row.token, row.body, row.headers); status != row.status {
			t.Fatal("cancel guard", status, row.status)
		}
	}
	var first string
	for range 2 {
		status, _, wire := h.request("POST", cancelPath, a.Access, "", read)
		var v map[string]any
		_ = json.Unmarshal(wire, &v)
		if status != 200 || v["status"] != "canceled" {
			t.Fatal("cancel", status, string(wire))
		}
		if first != "" && first != string(wire) {
			t.Fatal("cancel replay changed state")
		}
		first = string(wire)
	}
	if calls.Load() != 2 {
		t.Fatal("cancellation/replay sent another preview", calls.Load())
	}
	safeChild := randomID(t).String()
	if status, _, _ := h.request("POST", base+"/"+pending+"/replacement", a.Access, `{"job_id":"`+safeChild+`","accept_possible_duplicate":false}`, write); status != 202 {
		t.Fatal("known-unsent replacement", status)
	}
	if status, _, _ := h.request("POST", cancelPath, a.Access, "", read); status != 200 {
		t.Fatal(status)
	}
	// Repeating cancellation on the source cannot silently cancel the new child.
	waitStatus(safeChild, "queued")
	if status, _, _ := h.request("POST", base+"/"+safeChild+"/cancel", a.Access, "", read); status != 200 {
		t.Fatal(status)
	}
	waitStatus(source, "uncertain")
	// The deferred release cooperatively drains the selected worker at cleanup.
}
