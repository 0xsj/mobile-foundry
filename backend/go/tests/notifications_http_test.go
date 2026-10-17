package tests

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

func TestNotificationsHTTPCursorScopeAndHead(t *testing.T) {
	ctx := context.Background()
	scope := app.Scope{Actor: randomID(t), Account: randomID(t)}
	s := memory.New()
	first, second := notificationDelivery(t, scope), notificationDelivery(t, scope)
	for _, d := range []app.Delivery{first, second} {
		if err := s.Deliver(ctx, d, time.Now()); err != nil {
			t.Fatal(err)
		}
	}
	resolver, err := principal.NewDevelopment("notification-test-token", scope.Actor)
	if err != nil {
		t.Fatal(err)
	}
	mux := http.NewServeMux()
	notifications.New(s, notificationAccess{allowed: true}, clock.System{}, resolver).Routes(mux)
	api := httptest.NewServer(mux)
	defer api.Close()
	h := harness{t, api.URL}
	path := "/v1/accounts/" + scope.Account.String() + "/notifications"
	status, _, wire := h.request("GET", path+"?limit=1", "notification-test-token", "", nil)
	var page struct {
		Items []struct {
			ID id.ID `json:"id"`
		} `json:"items"`
		Next *string `json:"next_cursor"`
	}
	if json.Unmarshal(wire, &page) != nil || status != 200 || len(page.Items) != 1 || page.Items[0].ID != second.EventID || page.Next == nil {
		t.Fatal("first page", status, string(wire))
	}
	cursor := url.QueryEscape(*page.Next)
	status, _, wire = h.request("GET", path+"?limit=1&cursor="+cursor, "notification-test-token", "", nil)
	if json.Unmarshal(wire, &page) != nil || status != 200 || len(page.Items) != 1 || page.Items[0].ID != first.EventID || page.Next != nil {
		t.Fatal("second page", status, string(wire))
	}
	for _, route := range []string{path + "?unread=true&cursor=" + cursor, "/v1/accounts/" + randomID(t).String() + "/notifications?cursor=" + cursor} {
		if status, _, _ := h.request("GET", route, "notification-test-token", "", nil); status != 400 {
			t.Fatal("cursor crossed scope/filter", status)
		}
	}
	status, headers, wire := h.request("HEAD", path, "notification-test-token", "", nil)
	if status != 200 || len(wire) != 0 || headers.Get("Cache-Control") != "no-store" {
		t.Fatal("HEAD", status, len(wire))
	}
}
