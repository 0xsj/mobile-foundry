package tests

import (
	"context"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/account"
	"github.com/0xsj/mobile-foundry/backend/internal/notes"
	"github.com/0xsj/mobile-foundry/backend/internal/realtime"
	rtquery "github.com/0xsj/mobile-foundry/backend/internal/realtime/app/query"
	rtlocal "github.com/0xsj/mobile-foundry/backend/internal/realtime/infra/local"
	rtv1 "github.com/0xsj/mobile-foundry/backend/internal/realtime/transport/websocket/v1"
	"github.com/0xsj/mobile-foundry/backend/internal/server"
	"github.com/0xsj/mobile-foundry/backend/pkg/broadcast"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestRealtimeCommittedNotesHintsScopeAndReconnect(t *testing.T) {
	hub := broadcast.New(16, 4)
	defer hub.Close()
	runtime := module.Runtime{Clock: clock.System{}, IDs: id.Random{}, Capabilities: capability.NewRegistry(), Events: events.NewMemory(), Signals: hub, Principals: identities{"owner": id.ID("00000000-0000-4000-8000-000000000001"), "other": id.ID("00000000-0000-4000-8000-000000000002")}}
	units, err := module.Assemble(runtime, account.Build, notes.Build, realtime.Build)
	if err != nil {
		t.Fatal(err)
	}
	defer units.Close()
	app, err := server.New(config.Runtime{ShutdownTimeout: time.Second}, slog.New(slog.NewJSONHandler(io.Discard, nil)), units)
	if err != nil {
		t.Fatal(err)
	}
	api := httptest.NewServer(app.Handler())
	defer api.Close()
	h := harness{t, api.URL}
	status, _, body := h.request("POST", "/v1/accounts", "owner", `{"name":"Realtime"}`, nil)
	if status != 201 {
		t.Fatal(status)
	}
	var account struct {
		ID string `json:"id"`
	}
	_ = json.Unmarshal(body, &account)
	target := "ws" + strings.TrimPrefix(api.URL, "http") + "/v1/accounts/" + account.ID + "/realtime"
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	for _, headers := range []http.Header{{}, {"Authorization": []string{"Bearer other"}}, {"Authorization": []string{"Bearer owner"}, "Origin": []string{"https://foreign.example"}}, {"Authorization": []string{"Bearer owner"}, "Cookie": []string{"session=untrusted"}}} {
		c, response, err := websocket.Dial(ctx, target, &websocket.DialOptions{HTTPHeader: headers})
		if c != nil {
			_ = c.CloseNow()
		}
		if err == nil || response == nil || response.StatusCode < 400 {
			t.Fatal("unauthorized socket admitted")
		}
	}
	connect := func() *websocket.Conn {
		c, _, err := websocket.Dial(ctx, target, &websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer owner"}}})
		if err != nil {
			t.Fatal(err)
		}
		var ready struct {
			Type    string
			Refetch bool
		}
		if err = wsjson.Read(ctx, c, &ready); err != nil || ready.Type != "ready" || !ready.Refetch {
			t.Fatal("missing refetch handshake", err)
		}
		return c
	}
	c := connect()
	defer c.CloseNow()
	status, _, _ = h.request("POST", "/v1/accounts/"+account.ID+"/notes", "owner", `{"title":"Socket private title"}`, map[string]string{"Idempotency-Key": freshID(t).String()})
	if status != 201 {
		t.Fatal(status)
	}
	var message map[string]any
	if err = wsjson.Read(ctx, c, &message); err != nil || message["type"] != "changed" || message["account_id"] != account.ID {
		t.Fatal("commit hint missing", message, err)
	}
	encoded, _ := json.Marshal(message)
	if strings.Contains(string(encoded), "Socket private title") {
		t.Fatal("private content in hint")
	}
	_ = c.CloseNow()
	reopened := connect()
	_ = reopened.CloseNow()
}

type dynamicResolver struct {
	active atomic.Bool
	actor  id.ID
}

func (r *dynamicResolver) Resolve(ctx context.Context, _ string) (principal.Principal, error) {
	if !r.active.Load() {
		return principal.Principal{}, principal.ErrRequired
	}
	return principal.Principal{ID: r.actor}, ctx.Err()
}

type openRealtimeAccount struct{}

func (openRealtimeAccount) Allowed(context.Context, id.ID, id.ID) (bool, error) { return true, nil }
func TestRealtimeBeyondHTTPBudgetRevocationAndStop(t *testing.T) {
	hub := broadcast.New(2, 2)
	defer hub.Close()
	resolver := &dynamicResolver{actor: freshID(t)}
	resolver.active.Store(true)
	handler := &rtv1.Handler{Principals: resolver, Subscribe: rtquery.Subscribe{Access: openRealtimeAccount{}, Feed: rtlocal.Feed{Hub: hub}}, Recheck: 100 * time.Millisecond, Lifetime: 25 * time.Second}
	mux := http.NewServeMux()
	handler.Register(mux)
	api := httptest.NewServer(httpx.Boundary(mux, id.Random{}, slog.New(slog.NewJSONHandler(io.Discard, nil))))
	defer api.Close()
	defer handler.Stop()
	ctx, cancel := context.WithTimeout(context.Background(), 23*time.Second)
	defer cancel()
	target := "ws" + strings.TrimPrefix(api.URL, "http") + "/v1/accounts/" + freshID(t).String() + "/realtime"
	c, _, err := websocket.Dial(ctx, target, &websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer private-token"}}})
	if err != nil {
		t.Fatal(err)
	}
	defer c.CloseNow()
	var ready map[string]any
	if err = wsjson.Read(ctx, c, &ready); err != nil {
		t.Fatal(err)
	}
	// Keep reading to answer native ping frames past the ordinary 15-second boundary.
	closed := c.CloseRead(ctx)
	timer := time.NewTimer(16 * time.Second)
	defer timer.Stop()
	select {
	case <-closed.Done():
		t.Fatal("socket inherited HTTP request budget")
	case <-timer.C:
	}
	resolver.active.Store(false)
	select {
	case <-closed.Done():
	case <-time.After(2 * time.Second):
		t.Fatal("revoked bearer retained connection")
	}
	resolver.active.Store(true)
	second, _, err := websocket.Dial(ctx, target, &websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer private-token"}}})
	if err != nil {
		t.Fatal(err)
	}
	defer second.CloseNow()
	if err = wsjson.Read(ctx, second, &ready); err != nil {
		t.Fatal(err)
	}
	ended := second.CloseRead(ctx)
	if err = handler.Stop(); err != nil {
		t.Fatal(err)
	}
	select {
	case <-ended.Done():
	case <-time.After(time.Second):
		t.Fatal("socket survived module stop")
	}
}
