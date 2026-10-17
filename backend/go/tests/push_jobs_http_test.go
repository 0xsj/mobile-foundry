package tests

import (
	"context"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestPushJobHTTPGuardsReplayAndWorker(t *testing.T) {
	var calls atomic.Int64
	h := pushHTTP(t, gatewayFunc(func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error) {
		calls.Add(1)
		return app.PushAccepted, nil
	}), true)
	for _, name := range []string{"job-owner", "job-foreign"} {
		if status, _, _ := h.request("POST", "/v1/identity/register", "", `{"login":"`+name+`","display_name":"Person","password":"private test passphrase"}`, nil); status != 201 {
			t.Fatal(status)
		}
	}
	a, b := login(t, h, "job-owner"), login(t, h, "job-foreign")
	proof, _ := installationProof(t)
	wrong, _ := installationProof(t)
	installation := randomID(t).String()
	job := randomID(t).String()
	create := `{"installation_id":"` + installation + `","registration_id":"` + randomID(t).String() + `","provider":"fcm","application":"dev.example.jobs","environment":"production","token":"private-queued-token"}`
	if status, _, _ := h.request("POST", "/v1/installations", a.Access, create, map[string]string{"X-Installation-Key": proof}); status != 201 {
		t.Fatal(status)
	}
	path := "/v1/installations/" + installation + "/push-jobs"
	body := `{"job_id":"` + job + `"}`
	headers := map[string]string{"X-Installation-Key": proof, "If-Match": `"1"`}
	for _, row := range []struct {
		token, path, body string
		headers           map[string]string
		status            int
	}{
		{"", path, body, headers, 401}, {b.Access, path, body, headers, 403},
		{a.Access, path, body, map[string]string{"X-Installation-Key": wrong, "If-Match": `"1"`}, 404},
		{a.Access, path, body, map[string]string{"X-Installation-Key": proof}, 428},
		{a.Access, path, body, map[string]string{"X-Installation-Key": proof, "If-Match": `"2"`}, 409},
		{a.Access, path, `{"job_id":"` + job + `","account_id":"untrusted"}`, headers, 400},
		{a.Access, path + "?token=untrusted", body, headers, 400},
	} {
		status, _, _ := h.request("POST", row.path, row.token, row.body, row.headers)
		if status != row.status || calls.Load() != 0 {
			t.Fatal("admission", status, row.status)
		}
	}
	for range 2 {
		status, hdr, wire := h.request("POST", path, a.Access, body, headers)
		var value map[string]any
		if status != 202 || json.Unmarshal(wire, &value) != nil || len(value) != 8 || value["job_id"] != job || hdr.Get("ETag") != "" || hdr.Get("Cache-Control") != "no-store" {
			t.Fatal("queue", status, string(wire))
		}
		for _, private := range []string{proof, "private-queued-token", "principal_id", "session_id", "claim"} {
			if strings.Contains(string(wire), private) {
				t.Fatal("private job projection")
			}
		}
	}
	readHeaders := map[string]string{"X-Installation-Key": proof}
	deadline := time.Now().Add(5 * time.Second)
	for {
		status, _, wire := h.request("GET", path+"/"+job, a.Access, "", readHeaders)
		var value map[string]any
		_ = json.Unmarshal(wire, &value)
		if status != 200 {
			t.Fatal("status lookup", status)
		}
		if value["status"] == "accepted" {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("worker did not settle", string(wire))
		}
		time.Sleep(10 * time.Millisecond)
	}
	if calls.Load() != 1 {
		t.Fatal("enqueue replay caused duplicate send", calls.Load())
	}
	if status, _, _ := h.request("GET", path+"/"+job, b.Access, "", readHeaders); status != 403 {
		t.Fatal("foreign status", status)
	}
	if status, _, _ := h.request("GET", path+"/"+randomID(t).String(), a.Access, "", readHeaders); status != 404 {
		t.Fatal("missing status", status)
	}
	if status, _, _ := h.request("POST", path, a.Access, body, map[string]string{"X-Installation-Key": proof, "If-Match": `"2"`}); status != 409 {
		t.Fatal("changed replay intent", status)
	}
	if status, _, _ := h.request("GET", path+"/"+job, a.Access, "", headers); status != 400 {
		t.Fatal("unexpected status precondition", status)
	}
	if status, _, _ := h.request("GET", path+"/"+job, a.Access, `{}`, readHeaders); status != 400 {
		t.Fatal("status body accepted", status)
	}
}
func TestPushJobsRequireExplicitSelection(t *testing.T) {
	h := pushHTTP(t, gatewayFunc(func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error) {
		t.Error("unselected job send")
		return app.PushAccepted, nil
	}))
	if status, _, _ := h.request("POST", "/v1/installations/"+randomID(t).String()+"/push-jobs", "", `{}`, nil); status != 404 {
		t.Fatal("jobs enabled by provider selection", status)
	}
	path := "/v1/installations/" + randomID(t).String() + "/push-jobs/" + randomID(t).String()
	for _, suffix := range []string{"/cancel", "/replacement"} {
		if status, _, _ := h.request("POST", path+suffix, "", `{}`, nil); status != 404 {
			t.Fatal("recovery selected by provider alone", status)
		}
	}
}
