package tests

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/account"
	"github.com/0xsj/mobile-foundry/backend/internal/audit"
	"github.com/0xsj/mobile-foundry/backend/internal/identity"
	"github.com/0xsj/mobile-foundry/backend/internal/server"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

type authGrant struct{ Access, Refresh string }

func identitySetup(t *testing.T) harness {
	t.Helper()
	registry := capability.NewRegistry()
	runtime := module.Runtime{Clock: clock.System{}, IDs: id.Random{}, Capabilities: registry, Events: events.NewMemory(), Principals: identity.CredentialResolver(registry.Client("api"))}
	units, err := module.Assemble(runtime, account.Build, audit.Build, identity.Build)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = units.Close() })
	app, err := server.New(config.Runtime{ShutdownTimeout: time.Second}, slog.New(slog.NewJSONHandler(io.Discard, nil)), units)
	if err != nil {
		t.Fatal(err)
	}
	api := httptest.NewServer(app.Handler())
	t.Cleanup(api.Close)
	return harness{t, api.URL}
}
func login(t *testing.T, h harness, name string) authGrant {
	t.Helper()
	status, headers, wire := h.request("POST", "/v1/identity/sign-in", "", `{"login":"`+name+`","password":"private test passphrase"}`, nil)
	if status != 200 || headers.Get("Cache-Control") != "no-store" {
		t.Fatal(status, string(wire))
	}
	var result struct {
		Access  string `json:"access_token"`
		Refresh string `json:"refresh_token"`
	}
	if json.Unmarshal(wire, &result) != nil || result.Access == "" || result.Refresh == "" {
		t.Fatal("missing credentials")
	}
	return authGrant{result.Access, result.Refresh}
}
func TestRealIdentityHTTPAndAuditScopes(t *testing.T) {
	h := identitySetup(t)
	for _, name := range []string{"alex", "sam"} {
		status, _, wire := h.request("POST", "/v1/identity/register", "", `{"login":"`+name+`","display_name":"Person","password":"private test passphrase"}`, nil)
		if status != 201 || strings.Contains(string(wire), "password") {
			t.Fatal(status, string(wire))
		}
	}
	for _, name := range []string{"alex", "missing"} {
		status, _, wire := h.request("POST", "/v1/identity/sign-in", "", `{"login":"`+name+`","password":"incorrect test passphrase"}`, nil)
		if status != 401 || strings.Contains(string(wire), name) {
			t.Fatal("credential failure leaked login", status, string(wire))
		}
	}
	alex, sam := login(t, h, "alex"), login(t, h, "sam")
	status, _, wire := h.request("POST", "/v1/accounts", alex.Access, `{"name":"Personal"}`, nil)
	if status != 201 {
		t.Fatal(status, string(wire))
	}
	var account struct {
		ID string `json:"id"`
	}
	_ = json.Unmarshal(wire, &account)
	if status, _, _ := h.request("GET", "/v1/accounts/"+account.ID, sam.Access, "", nil); status != 403 {
		t.Fatal("real principal crossed account scope", status)
	}
	status, _, wire = h.request("POST", "/v1/identity/renew", "", `{"refresh_token":"`+alex.Refresh+`"}`, nil)
	if status != 200 {
		t.Fatal(status, string(wire))
	}
	var next struct {
		Access  string `json:"access_token"`
		Refresh string `json:"refresh_token"`
	}
	_ = json.Unmarshal(wire, &next)
	if status, _, _ := h.request("GET", "/v1/identity/me", alex.Access, "", nil); status != 401 {
		t.Fatal("previous access survived rotation", status)
	}
	if status, _, _ := h.request("POST", "/v1/identity/renew", "", `{"refresh_token":"`+alex.Refresh+`"}`, nil); status != 401 {
		t.Fatal("consumed refresh accepted", status)
	}
	if status, _, _ := h.request("GET", "/v1/identity/me", next.Access, "", nil); status != 401 {
		t.Fatal("reuse did not invalidate active grant", status)
	}
	alex = login(t, h, "alex")
	deadline := time.Now().Add(3 * time.Second)
	var cursor string
	for {
		status, _, wire = h.request("GET", "/v1/principals/me/audit?limit=1", alex.Access, "", nil)
		if status != 200 {
			t.Fatal(status, string(wire))
		}
		var page struct {
			Entries []map[string]any `json:"entries"`
			Next    *string          `json:"next_cursor"`
		}
		_ = json.Unmarshal(wire, &page)
		if page.Next != nil {
			cursor = *page.Next
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("identity events did not reach Audit")
		}
		time.Sleep(20 * time.Millisecond)
	}
	if status, _, _ := h.request("GET", "/v1/principals/me/audit?cursor="+cursor, sam.Access, "", nil); status != 400 {
		t.Fatal("principal cursor crossed scope", status)
	}
	for {
		status, _, wire = h.request("GET", "/v1/principals/me/audit", alex.Access, "", nil)
		if status != 200 || strings.Contains(string(wire), "private test passphrase") || strings.Contains(string(wire), "login") || strings.Contains(string(wire), alex.Access) || strings.Contains(string(wire), alex.Refresh) {
			t.Fatal("identity Audit projection failed", status)
		}
		if strings.Contains(string(wire), "refresh_reuse") {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("revocation fact did not reach Audit")
		}
		time.Sleep(20 * time.Millisecond)
	}
	for range 2 {
		if status, _, _ := h.request("POST", "/v1/identity/sign-out", "", `{"refresh_token":"`+alex.Refresh+`"}`, nil); status != 204 {
			t.Fatal("sign out failed", status)
		}
	}
	if status, _, _ := h.request("GET", "/v1/identity/me", alex.Access, "", nil); status != 401 {
		t.Fatal("sign out did not revoke", status)
	}
	if status, _, _ := h.request("POST", "/v1/identity/sign-in", "", `{"login":"sam","password":"private test passphrase","principal_id":"spoofed"}`, nil); status != 400 {
		t.Fatal("unknown auth body admitted", status)
	}
}
func TestIdentityCapabilityCallerAndDependencyFailure(t *testing.T) {
	registry := capability.NewRegistry()
	resolver := identity.CredentialResolver(registry.Client("api"))
	if _, err := resolver.Resolve(context.Background(), "a1."+strings.Repeat("A", 43)); !errors.Is(err, capability.ErrMissing) {
		t.Fatal("dependency failure disguised as bad credential", err)
	}
	if _, err := resolver.Resolve(context.Background(), strings.Repeat("A", 20000)); err == nil {
		t.Fatal("oversized bearer was admitted")
	}
}
