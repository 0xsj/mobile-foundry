package tests

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/account"
	"github.com/0xsj/mobile-foundry/backend/internal/audit"
	"github.com/0xsj/mobile-foundry/backend/internal/notes"
	"github.com/0xsj/mobile-foundry/backend/internal/server"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type identities map[string]id.ID

func (r identities) Resolve(ctx context.Context, credential string) (principal.Principal, error) {
	if err := ctx.Err(); err != nil {
		return principal.Principal{}, err
	}
	identity, found := r[credential]
	if !found {
		return principal.Principal{}, principal.ErrRequired
	}
	return principal.Principal{ID: identity}, nil
}

type harness struct {
	t       *testing.T
	address string
}

func setup(t *testing.T, bus events.Bus) harness {
	t.Helper()
	runtime := module.Runtime{Clock: clock.System{}, IDs: id.Random{}, Capabilities: capability.NewRegistry(), Events: bus, Principals: identities{"owner": id.ID("00000000-0000-4000-8000-000000000001"), "other": id.ID("00000000-0000-4000-8000-000000000002")}}
	units, err := module.Assemble(runtime, account.Build, audit.Build, notes.Build)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = units.Close() })
	app, err := server.New(config.Runtime{ShutdownTimeout: time.Second}, slog.New(slog.NewJSONHandler(io.Discard, nil)), units)
	if err != nil {
		t.Fatal(err)
	}
	httpServer := httptest.NewServer(app.Handler())
	t.Cleanup(httpServer.Close)
	return harness{t, httpServer.URL}
}
func (h harness) request(method, path, token, body string, headers map[string]string) (int, http.Header, []byte) {
	h.t.Helper()
	req, err := http.NewRequest(method, h.address+path, strings.NewReader(body))
	if err != nil {
		h.t.Fatal(err)
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	if body != "" {
		req.Header.Set("Content-Type", "application/json")
	}
	for key, value := range headers {
		req.Header.Set(key, value)
	}
	client := http.Client{Timeout: 3 * time.Second}
	res, err := client.Do(req)
	if err != nil {
		h.t.Fatal(err)
	}
	defer res.Body.Close()
	wire, err := io.ReadAll(res.Body)
	if err != nil {
		h.t.Fatal(err)
	}
	return res.StatusCode, res.Header, wire
}
func (h harness) create(name string) (string, http.Header) {
	h.t.Helper()
	status, headers, body := h.request("POST", "/v1/accounts", "owner", `{"name":"`+name+`"}`, map[string]string{"X-Correlation-ID": "workflow.1"})
	if status != 201 {
		h.t.Fatal(status, string(body))
	}
	var result struct {
		ID string `json:"id"`
	}
	if json.Unmarshal(body, &result) != nil || result.ID == "" {
		h.t.Fatal("missing created ID")
	}
	return result.ID, headers
}

type auditPage struct {
	Entries []struct {
		EventID       string `json:"event_id"`
		Action        string `json:"action"`
		Version       uint64 `json:"aggregate_version"`
		RequestID     string `json:"request_id"`
		CorrelationID string `json:"correlation_id"`
	} `json:"entries"`
	Next *string `json:"next_cursor"`
}

func (h harness) waitFor(accountID string, count int) auditPage {
	h.t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		status, _, wire := h.request("GET", "/v1/accounts/"+accountID+"/audit", "owner", "", nil)
		var page auditPage
		if status != 200 || json.Unmarshal(wire, &page) != nil {
			h.t.Fatal(status, string(wire))
		}
		if len(page.Entries) == count {
			return page
		}
		time.Sleep(10 * time.Millisecond)
	}
	h.t.Fatal("audit did not settle")
	return auditPage{}
}
func TestAccountAuditHTTPWorkflowAndScope(t *testing.T) {
	h := setup(t, events.NewMemory())
	identity, headers := h.create("Private account name")
	path := "/v1/accounts/" + identity
	if headers.Get("ETag") != `"1"` || headers.Get("Location") != path {
		t.Fatal("create metadata missing")
	}
	status, _, wire := h.request("GET", path, "owner", "", nil)
	if status != 200 || !bytes.Contains(wire, []byte("Private account name")) {
		t.Fatal(status, string(wire))
	}
	status, head, wire := h.request("HEAD", path, "owner", "", nil)
	if status != 200 || len(wire) != 0 || head.Get("ETag") != `"1"` {
		t.Fatal("HEAD mismatch")
	}
	status, renamed, _ := h.request("PATCH", path, "owner", `{"name":"Renamed private name"}`, map[string]string{"If-Match": `"1"`})
	if status != 200 || renamed.Get("ETag") != `"2"` {
		t.Fatal("rename failed", status)
	}
	page := h.waitFor(identity, 2)
	if page.Entries[0].Action != "account.created" || page.Entries[1].Action != "account.renamed" || page.Entries[1].Version != 2 || page.Entries[0].RequestID != headers.Get("X-Request-ID") || page.Entries[0].CorrelationID != "workflow.1" {
		t.Fatal("evidence/provenance mismatch", page)
	}
	for _, route := range []string{path, path + "/audit"} {
		status, _, _ = h.request("GET", route, "other", "", nil)
		if status != 403 {
			t.Fatal("cross-principal access accepted", route, status)
		}
	}
	status, _, wire = h.request("GET", path+"/audit?limit=1", "owner", "", nil)
	if status != 200 || json.Unmarshal(wire, &page) != nil || len(page.Entries) != 1 || page.Next == nil {
		t.Fatal("pagination failed", string(wire))
	}
	status, _, wire = h.request("GET", path+"/audit?cursor="+*page.Next, "owner", "", nil)
	if status != 200 || json.Unmarshal(wire, &page) != nil || len(page.Entries) != 1 || page.Entries[0].Version != 2 || page.Next != nil {
		t.Fatal("cursor continuation failed", string(wire))
	}
	if bytes.Contains(wire, []byte("private")) {
		t.Fatal("audit retained account content")
	}
}
func TestHTTPAdmissionRefusesInvalidAndStaleWrites(t *testing.T) {
	h := setup(t, events.NewMemory())
	identity, _ := h.create("Original")
	path := "/v1/accounts/" + identity
	for _, sample := range []struct {
		method, path, token, body string
		headers                   map[string]string
		status                    int
	}{
		{"POST", "/v1/accounts", "", `{"name":"X"}`, nil, 401},
		{"POST", "/v1/accounts", "owner", `{"name":" "}`, nil, 400},
		{"POST", "/v1/accounts", "owner", `{"name":"X","owner_id":"spoof"}`, nil, 400},
		{"POST", "/v1/accounts", "owner", `null`, nil, 400},
		{"POST", "/v1/accounts", "owner", `{"name":"X"}`, map[string]string{"Idempotency-Key": "unsupported"}, 400},
		{"PATCH", path, "owner", `{"name":"X"}`, nil, 428},
		{"PATCH", path, "owner", `{"name":"X"}`, map[string]string{"If-Match": `W/"1"`}, 400},
		{"PATCH", path, "owner", `{"name":"X"}`, map[string]string{"If-Match": `"2"`}, 409},
		{"GET", path + "/audit?limit=0", "owner", "", nil, 400},
		{"GET", path + "/audit?limit=1&limit=2", "owner", "", nil, 400},
		{"GET", path + "/audit?cursor=bad", "owner", "", nil, 400},
		{"GET", path + "/audit?unknown=x", "owner", "", nil, 400},
		{"GET", "/v1/accounts/invalid", "owner", "", nil, 400},
		{"DELETE", path, "owner", "", nil, 405},
	} {
		status, headers, body := h.request(sample.method, sample.path, sample.token, sample.body, sample.headers)
		if status != sample.status || headers.Get("Content-Type") != "application/problem+json; charset=utf-8" {
			t.Fatalf("%s %s: %d %s", sample.method, sample.path, status, body)
		}
	}
	page := h.waitFor(identity, 1)
	if page.Entries[0].Version != 1 {
		t.Fatal("failed write produced evidence")
	}
}

type lostAck struct {
	*events.Memory
	calls atomic.Int32
}

func (b *lostAck) Publish(ctx context.Context, message events.Envelope) error {
	if err := b.Memory.Publish(ctx, message); err != nil {
		return err
	}
	if b.calls.Add(1) == 1 {
		return errors.New("delivery acknowledgment lost")
	}
	return nil
}
func TestReplayAfterLostAcknowledgmentDoesNotDuplicateEvidence(t *testing.T) {
	bus := &lostAck{Memory: events.NewMemory()}
	h := setup(t, bus)
	identity, _ := h.create("Replay")
	h.waitFor(identity, 1)
	deadline := time.Now().Add(3 * time.Second)
	for bus.calls.Load() < 2 && time.Now().Before(deadline) {
		time.Sleep(10 * time.Millisecond)
	}
	if bus.calls.Load() != 2 {
		t.Fatal("event was not retried")
	}
	page := h.waitFor(identity, 1)
	if len(page.Entries) != 1 {
		t.Fatal("replay duplicated evidence")
	}
}
