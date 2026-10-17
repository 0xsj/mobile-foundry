package tests

import (
	"context"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/account"
	"github.com/0xsj/mobile-foundry/backend/internal/audit"
	"github.com/0xsj/mobile-foundry/backend/internal/identity"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/server"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"io"
	"log/slog"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

type privateMail struct {
	mu       sync.Mutex
	messages []app.Mail
	failed   bool
	attempts int
}

func (m *privateMail) Send(_ context.Context, v app.Mail) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.attempts++
	if m.failed {
		return app.ErrMail
	}
	m.messages = append(m.messages, v)
	return nil
}
func (m *privateMail) last(t *testing.T, purpose string) app.Mail {
	t.Helper()
	m.mu.Lock()
	defer m.mu.Unlock()
	for i := len(m.messages) - 1; i >= 0; i-- {
		if m.messages[i].Purpose == purpose {
			return m.messages[i]
		}
	}
	t.Fatal("no mail for purpose", purpose)
	return app.Mail{}
}
func (m *privateMail) waitAttempts(t *testing.T, count int) {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for {
		m.mu.Lock()
		done := m.attempts >= count
		m.mu.Unlock()
		if done {
			return
		}
		if time.Now().After(deadline) {
			t.Fatal("mail worker did not settle")
		}
		time.Sleep(5 * time.Millisecond)
	}
}
func recoveryHTTP(t *testing.T) (harness, *privateMail) {
	t.Helper()
	mail := &privateMail{}
	registry := capability.NewRegistry()
	runtime := module.Runtime{Clock: clock.System{}, IDs: id.Random{}, Capabilities: registry, Events: events.NewMemory(), Principals: identity.CredentialResolver(registry.Client("api"))}
	units, err := module.Assemble(runtime, account.Build, audit.Build, identity.BuildWithMail(mail))
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
	return harness{t, api.URL}, mail
}
func TestIdentityRecoveryHTTPAndAudit(t *testing.T) {
	h, mail := recoveryHTTP(t)
	if status, _, _ := h.request("POST", "/v1/identity/register", "", `{"login":"recover-http","display_name":"Person","password":"private test passphrase"}`, nil); status != 201 {
		t.Fatal(status)
	}
	grant := login(t, h, "recover-http")
	if status, _, _ := h.request("POST", "/v1/identity/email/request", grant.Access, `{"email":"recover@example.test","password":"wrong private password"}`, nil); status != 401 {
		t.Fatal("verification omitted reauthentication", status)
	}
	if status, _, wire := h.request("POST", "/v1/identity/email/request", grant.Access, `{"email":"recover@example.test","password":"private test passphrase"}`, nil); status != 204 || len(wire) != 0 {
		t.Fatal(status, string(wire))
	}
	code := mail.last(t, domain.VerifyEmail).Token
	if status, _, _ := h.request("POST", "/v1/identity/email/verify", "", `{"token":"`+code+`"}`, nil); status != 204 {
		t.Fatal("verification", status)
	}
	if status, _, wire := h.request("GET", "/v1/identity/email", grant.Access, "", nil); status != 200 || !strings.Contains(string(wire), `"verified":true`) {
		t.Fatal(status, string(wire))
	}
	status, _, missing := h.request("POST", "/v1/identity/password/request-reset", "", `{"email":"missing@example.test"}`, nil)
	if status != 202 {
		t.Fatal(status)
	}
	status, _, found := h.request("POST", "/v1/identity/password/request-reset", "", `{"email":"recover@example.test"}`, nil)
	if status != 202 || string(found) != string(missing) {
		t.Fatal("recovery existence disclosed")
	}
	mail.waitAttempts(t, 2)
	reset := mail.last(t, domain.ResetPassword).Token
	mail.mu.Lock()
	mail.failed = true
	mail.mu.Unlock()
	if status, _, failed := h.request("POST", "/v1/identity/password/request-reset", "", `{"email":"recover@example.test"}`, nil); status != 202 || string(failed) != string(missing) {
		t.Fatal("mail failure disclosed existence")
	}
	mail.waitAttempts(t, 3)
	mail.mu.Lock()
	mail.failed = false
	mail.mu.Unlock()
	// A later request replaces the outstanding challenge, including one whose mail failed.
	_, _, _ = h.request("POST", "/v1/identity/password/request-reset", "", `{"email":"recover@example.test"}`, nil)
	if status, _, _ := h.request("POST", "/v1/identity/password/reset", "", `{"token":"`+reset+`","password":"changed private passphrase"}`, nil); status != 400 {
		t.Fatal("replaced reset token admitted", status)
	}
	mail.waitAttempts(t, 4)
	reset = mail.last(t, domain.ResetPassword).Token
	if status, _, _ := h.request("POST", "/v1/identity/password/reset", "", `{"token":"`+reset+`","password":"changed private passphrase"}`, nil); status != 204 {
		t.Fatal("reset", status)
	}
	if status, _, _ := h.request("GET", "/v1/identity/me", grant.Access, "", nil); status != 401 {
		t.Fatal("reset left access active", status)
	}
	status, _, wire := h.request("POST", "/v1/identity/sign-in", "", `{"login":"recover-http","password":"changed private passphrase"}`, nil)
	if status != 200 {
		t.Fatal(status, string(wire))
	}
	var g struct {
		Access string `json:"access_token"`
	}
	_ = json.Unmarshal(wire, &g)
	deadline := time.Now().Add(5 * time.Second)
	for {
		status, _, wire = h.request("GET", "/v1/principals/me/audit", g.Access, "", nil)
		if status == 200 && strings.Contains(string(wire), "identity.password.recovered") {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("lifecycle Audit delivery missing", status, string(wire))
		}
		time.Sleep(10 * time.Millisecond)
	}
	for _, secret := range []string{code, reset, "recover@example.test", "changed private passphrase"} {
		if strings.Contains(string(wire), secret) {
			t.Fatal("Audit leaked recovery input")
		}
	}
	if status, _, _ := h.request("POST", "/v1/identity/password/change", g.Access, `{"current_password":"changed private passphrase","new_password":"next private passphrase"}`, nil); status != 204 {
		t.Fatal("change password", status)
	}
	if status, _, _ := h.request("GET", "/v1/identity/me", g.Access, "", nil); status != 401 {
		t.Fatal("change retained access", status)
	}
}
