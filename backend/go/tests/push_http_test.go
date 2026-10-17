package tests

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http/httptest"
	"os"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/account"
	"github.com/0xsj/mobile-foundry/backend/internal/audit"
	"github.com/0xsj/mobile-foundry/backend/internal/identity"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/push"
	"github.com/0xsj/mobile-foundry/backend/internal/server"
	"github.com/0xsj/mobile-foundry/backend/migrations"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
)

func pushHTTP(t *testing.T, gateway app.PushGateway, queued ...bool) harness {
	t.Helper()
	registry := capability.NewRegistry()
	runtime := module.Runtime{Clock: clock.System{}, IDs: id.Random{}, Capabilities: registry, Events: events.NewMemory(), Principals: identity.CredentialResolver(registry.Client("api"))}
	identityBuilder := module.Builder(identity.Build)
	notificationsBuilder := notifications.BuildWithPush(gateway, queued...)
	if url := os.Getenv("TEST_POSTGRES_URL"); url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		identitySchema, err := storage.Load(migrations.Files, "identity")
		if err != nil {
			t.Fatal(err)
		}
		notificationsSchema, err := storage.Load(migrations.Files, "notifications")
		if err != nil {
			t.Fatal(err)
		}
		identityBuilder = identity.BuildPostgres(context.Background(), storage.Config{URL: url}, identitySchema)
		notificationsBuilder = notifications.BuildPostgresWithPush(context.Background(), storage.Config{URL: url}, notificationsSchema, gateway, queued...)
	}
	units, err := module.Assemble(runtime, account.Build, audit.Build, identityBuilder, notificationsBuilder)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = units.Close() })
	api, err := server.New(config.Runtime{ShutdownTimeout: time.Second}, slog.New(slog.NewJSONHandler(io.Discard, nil)), units)
	if err != nil {
		t.Fatal(err)
	}
	http := httptest.NewServer(api.Handler())
	t.Cleanup(http.Close)
	return harness{t, http.URL}
}

func TestPushPreviewHTTPProofScopeVersionAndOutcomes(t *testing.T) {
	var calls atomic.Int64
	var outcome atomic.Value
	outcome.Store(app.PushAccepted)
	gateway := gatewayFunc(func(_ context.Context, address app.PushAddress, preview id.ID) (app.PushDisposition, error) {
		calls.Add(1)
		if address.RegisteredAt.IsZero() || !preview.Valid() {
			t.Error("dispatch metadata")
		}
		if outcome.Load().(app.PushDisposition) == "unavailable" {
			return "", push.ErrUnavailable
		}
		return outcome.Load().(app.PushDisposition), nil
	})
	h := pushHTTP(t, gateway)
	for _, login := range []string{"push-alex", "push-sam"} {
		if status, _, _ := h.request("POST", "/v1/identity/register", "", `{"login":"`+login+`","display_name":"Person","password":"private test passphrase"}`, nil); status != 201 {
			t.Fatal(status)
		}
	}
	a, b := login(t, h, "push-alex"), login(t, h, "push-sam")
	proof, _ := installationProof(t)
	wrong, _ := installationProof(t)
	installation := randomID(t).String()
	preview := randomID(t).String()
	create := `{"installation_id":"` + installation + `","registration_id":"` + randomID(t).String() + `","provider":"fcm","application":"dev.example.app","environment":"production","token":"private-preview-address"}`
	if status, _, _ := h.request("POST", "/v1/installations", a.Access, create, map[string]string{"X-Installation-Key": proof}); status != 201 {
		t.Fatal("register", status)
	}
	path := "/v1/installations/" + installation + "/push-preview"
	body := `{"preview_id":"` + preview + `"}`
	headers := map[string]string{"X-Installation-Key": proof, "If-Match": `"1"`}
	for _, row := range []struct {
		name, token, path, body string
		headers                 map[string]string
		status                  int
	}{
		{"anonymous", "", path, body, headers, 401},
		{"foreign_session", b.Access, path, body, headers, 403},
		{"bad_proof", a.Access, path, body, map[string]string{"X-Installation-Key": wrong, "If-Match": `"1"`}, 404},
		{"missing_revision", a.Access, path, body, map[string]string{"X-Installation-Key": proof}, 428},
		{"stale_revision", a.Access, path, body, map[string]string{"X-Installation-Key": proof, "If-Match": `"2"`}, 409},
		{"unknown_input", a.Access, path, `{"preview_id":"` + preview + `","principal_id":"` + randomID(t).String() + `"}`, headers, 400},
		{"query", a.Access, path + "?token=ignored", body, headers, 400},
		{"invalid_preview", a.Access, path, `{"preview_id":"invalid"}`, headers, 400},
		{"forbidden_replay_header", a.Access, path, body, map[string]string{"X-Installation-Key": proof, "If-Match": `"1"`, "Idempotency-Key": "not-supported"}, 400},
	} {
		t.Run(row.name, func(t *testing.T) {
			status, _, wire := h.request("POST", row.path, row.token, row.body, row.headers)
			if status != row.status || calls.Load() != 0 {
				t.Fatal("guard failed", status)
			}
			if strings.Contains(string(wire), proof) || strings.Contains(string(wire), "private-preview-address") {
				t.Fatal("private response")
			}
		})
	}
	for range 2 {
		status, hdr, wire := h.request("POST", path, a.Access, body, headers)
		var response map[string]any
		if status != 200 || json.Unmarshal(wire, &response) != nil || len(response) != 3 || response["preview_id"] != preview || response["disposition"] != "accepted" || response["retired"] != false || hdr.Get("ETag") != "" || hdr.Get("Cache-Control") != "no-store" {
			t.Fatal("acceptance", status, string(wire))
		}
	}
	if calls.Load() != 2 {
		t.Fatal("preview ID was treated as idempotency")
	}
	outcome.Store(app.PushDisposition("unavailable"))
	if status, _, wire := h.request("POST", path, a.Access, body, headers); status != 503 || strings.Contains(string(wire), "private-preview-address") {
		t.Fatal("uncertain provider failure", status)
	}
	outcome.Store(app.PushInvalidTarget)
	status, _, wire := h.request("POST", path, a.Access, body, headers)
	if status != 200 || !strings.Contains(string(wire), `"retired":true`) {
		t.Fatal("retirement", status, string(wire))
	}
	if status, _, _ := h.request("POST", path, a.Access, body, headers); status != 409 {
		t.Fatal("old snapshot sent after retirement", status)
	}
	status, _, wire = h.request("GET", "/v1/installations/"+installation, a.Access, "", map[string]string{"X-Installation-Key": proof})
	if status != 200 || !strings.Contains(string(wire), `"registered":false`) || !strings.Contains(string(wire), `"version":2`) {
		t.Fatal("retired state", status, string(wire))
	}
	if calls.Load() != 4 {
		t.Fatal("automatic send replay", calls.Load())
	}
}

func TestPushDisabledRuntimeAndMissingAuthority(t *testing.T) {
	h := pushHTTP(t, nil)
	if status, _, _ := h.request("POST", "/v1/installations/"+randomID(t).String()+"/push-preview", "", `{}`, nil); status != 404 {
		t.Fatal("default exposed sender", status)
	}
	registry := capability.NewRegistry()
	runtime := module.Runtime{Clock: clock.System{}, IDs: id.Random{}, Capabilities: registry, Events: events.NewMemory()}
	// A selected sender must declare its authority requirement during assembly.
	runtime.Principals = identity.CredentialResolver(registry.Client("api"))
	_, err := module.Assemble(runtime, account.Build, audit.Build, notifications.BuildWithPush(gatewayFunc(func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error) {
		t.Fatal("startup sent push")
		return "", nil
	})))
	if err == nil {
		t.Fatal("missing Identity accepted", err)
	}
	gateway, closeGateway, err := notifications.LoadPushGateway("", clock.System{})
	if err != nil || gateway != nil {
		t.Fatal("default gateway selection", err)
	}
	if err := closeGateway(); err != nil {
		t.Fatal(err)
	}
}
