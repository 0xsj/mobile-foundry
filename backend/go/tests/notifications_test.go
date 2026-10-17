package tests

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/query"
	receiver "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/events"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/memory"
	pg "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"os"
	"strings"
	"sync"
	"testing"
	"time"
)

func notificationDelivery(t *testing.T, scope app.Scope) app.Delivery {
	t.Helper()
	return app.Delivery{EventID: randomID(t), AccountID: scope.Account, PrincipalID: scope.Actor, FileID: randomID(t), OccurredAt: time.Now().UTC().Truncate(time.Microsecond)}
}
func notificationsConformance(t *testing.T, s app.Store) {
	t.Helper()
	ctx := context.Background()
	scope := app.Scope{Actor: randomID(t), Account: randomID(t)}
	enabled, err := s.MediaEnabled(ctx, scope)
	if err != nil || !enabled {
		t.Fatal("default preference", err)
	}
	d := notificationDelivery(t, scope)
	var group sync.WaitGroup
	failures := make(chan error, 12)
	for range 12 {
		group.Go(func() { failures <- s.Deliver(ctx, d, time.Now()) })
	}
	group.Wait()
	for range 12 {
		if err := <-failures; err != nil {
			t.Fatal(err)
		}
	}
	input := app.ListInput{Scope: scope, Limit: 1}
	page, err := s.List(ctx, input)
	if err != nil || len(page.Items) != 1 || page.UnreadCount != 1 {
		t.Fatal("duplicate delivery", page, err)
	}
	first := page.Items[0].Snapshot()
	changed := d
	changed.FileID = randomID(t)
	if err := s.Deliver(ctx, changed, time.Now()); !errors.Is(err, app.ErrReplay) {
		t.Fatal("changed source accepted", err)
	}
	if err := s.Deliver(ctx, d, time.Now().Add(time.Hour)); err != nil {
		t.Fatal(err)
	}
	page, err = s.List(ctx, input)
	if err != nil || page.Items[0].Snapshot() != first {
		t.Fatal("redelivery changed arrival", err)
	}
	read, err := s.MarkRead(ctx, scope, d.EventID, time.Now())
	if err != nil || read.Snapshot().ReadAt.IsZero() {
		t.Fatal("read", err)
	}
	again, err := s.MarkRead(ctx, scope, d.EventID, time.Now().Add(time.Hour))
	if err != nil || again.Snapshot() != read.Snapshot() {
		t.Fatal("read repeat changed timestamp", err)
	}
	input.UnreadOnly = true
	page, err = s.List(ctx, input)
	if err != nil || len(page.Items) != 0 || page.UnreadCount != 0 {
		t.Fatal("unread filter", err)
	}
	for _, other := range []app.Scope{{Actor: randomID(t), Account: scope.Account}, {Actor: scope.Actor, Account: randomID(t)}} {
		if _, err := s.MarkRead(ctx, other, d.EventID, time.Now()); !errors.Is(err, app.ErrMissing) {
			t.Fatal("foreign item marked", err)
		}
		page, err := s.List(ctx, app.ListInput{Scope: other, Limit: 10})
		if err != nil || len(page.Items) != 0 || page.UnreadCount != 0 {
			t.Fatal("scope leak", err)
		}
	}
	if err := s.SetMediaEnabled(ctx, scope, false); err != nil {
		t.Fatal(err)
	}
	suppressed := notificationDelivery(t, scope)
	if err := s.Deliver(ctx, suppressed, time.Now()); err != nil {
		t.Fatal(err)
	}
	if err := s.SetMediaEnabled(ctx, scope, true); err != nil {
		t.Fatal(err)
	}
	if err := s.Deliver(ctx, suppressed, time.Now()); err != nil {
		t.Fatal(err)
	}
	page, err = s.List(ctx, app.ListInput{Scope: scope, Limit: 20})
	if err != nil || len(page.Items) != 1 {
		t.Fatal("suppression replayed after unmute", err)
	}
	// Arrival order, not producer timestamps, defines keyset pagination.
	second := notificationDelivery(t, scope)
	second.OccurredAt = d.OccurredAt.Add(-time.Hour)
	if err := s.Deliver(ctx, second, time.Now()); err != nil {
		t.Fatal(err)
	}
	page, err = s.List(ctx, app.ListInput{Scope: scope, Limit: 1})
	if err != nil || page.Items[0].Snapshot().ID != second.EventID || page.Next != 2 || page.UnreadCount != 1 {
		t.Fatal("arrival page", page, err)
	}
	page, err = s.List(ctx, app.ListInput{Scope: scope, Before: page.Next, Limit: 1})
	if err != nil || len(page.Items) != 1 || page.Items[0].Snapshot().ID != first.ID || page.Next != 0 {
		t.Fatal("next page", page, err)
	}
	canceled, cancel := context.WithCancel(ctx)
	cancel()
	if err := s.Deliver(canceled, notificationDelivery(t, scope), time.Now()); !errors.Is(err, context.Canceled) {
		t.Fatal("cancellation", err)
	}
	// Race preference updates with delivery; either decision is valid, but the
	// decision recorded on first delivery must survive all later redeliveries.
	for range 6 {
		race := notificationDelivery(t, scope)
		group.Go(func() { failures <- s.SetMediaEnabled(ctx, scope, false) })
		group.Go(func() { failures <- s.Deliver(ctx, race, time.Now()) })
		group.Wait()
		for range 2 {
			if err := <-failures; err != nil {
				t.Fatal(err)
			}
		}
		before, err := s.List(ctx, app.ListInput{Scope: scope, Limit: 100})
		if err != nil {
			t.Fatal(err)
		}
		if err := s.SetMediaEnabled(ctx, scope, true); err != nil {
			t.Fatal(err)
		}
		if err := s.Deliver(ctx, race, time.Now()); err != nil {
			t.Fatal(err)
		}
		after, err := s.List(ctx, app.ListInput{Scope: scope, Limit: 100})
		if err != nil || len(before.Items) != len(after.Items) {
			t.Fatal("race changed committed decision", err)
		}
	}
}
func TestNotificationsProviders(t *testing.T) {
	t.Run("memory", func(t *testing.T) { notificationsConformance(t, memory.New()) })
	if url := os.Getenv("TEST_POSTGRES_URL"); url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		t.Run("postgres", func(t *testing.T) {
			ctx := context.Background()
			pool, err := storage.Open(ctx, storage.Config{URL: url})
			if err != nil {
				t.Fatal(err)
			}
			defer pool.Close()
			s := pg.New(pool)
			notificationsConformance(t, s)
			// Force each half of delivery to fail independently. Neither item nor
			// dedup marker nor sequence may survive a rollback.
			for _, table := range []string{"items", "inbox", "scope_state"} {
				scope := app.Scope{Actor: randomID(t), Account: randomID(t)}
				d := notificationDelivery(t, scope)
				if _, err := pool.Exec(ctx, "ALTER TABLE notifications."+table+" ADD CONSTRAINT fail_notification CHECK(false) NOT VALID"); err != nil {
					t.Fatal(err)
				}
				err = s.Deliver(ctx, d, time.Now())
				if _, drop := pool.Exec(ctx, "ALTER TABLE notifications."+table+" DROP CONSTRAINT fail_notification"); drop != nil {
					t.Fatal(drop)
				}
				if err == nil {
					t.Fatal("forced transaction committed", table)
				}
				var n int
				if err := pool.QueryRow(ctx, `SELECT count(*) FROM notifications.inbox WHERE event_id=$1`, d.EventID.String()).Scan(&n); err != nil || n != 0 {
					t.Fatal("partial inbox", err)
				}
				if err := s.Deliver(ctx, d, time.Now()); err != nil {
					t.Fatal("retry after rollback", err)
				}
				page, err := s.List(ctx, app.ListInput{Scope: scope, Limit: 10})
				if err != nil || len(page.Items) != 1 || page.Items[0].Snapshot().Sequence != 1 {
					t.Fatal("partial item/sequence", err)
				}
			}
		})
	}
}

type notificationAccess struct {
	allowed bool
	err     error
}

func (a notificationAccess) Allowed(context.Context, id.ID, id.ID) (bool, error) {
	return a.allowed, a.err
}
func TestNotificationsAdmissionAndAccess(t *testing.T) {
	ctx := context.Background()
	s := memory.New()
	scope := app.Scope{Actor: randomID(t), Account: randomID(t)}
	d := notificationDelivery(t, scope)
	receive := receiver.SubscribeFiles(command.DeliverUpload{Store: s, Clock: clock.System{}})
	wire, _ := json.Marshal(map[string]any{"file_id": d.FileID, "account_id": d.AccountID, "version": 2})
	m := events.Envelope{ID: d.EventID, Name: "files.upload_completed.v1", Producer: "files", AccountID: d.AccountID, ActorID: d.PrincipalID, Version: 2, OccurredAt: d.OccurredAt, Data: wire}
	if err := receive.Handle(ctx, m); err != nil {
		t.Fatal(err)
	}
	for _, change := range []func(*events.Envelope){func(m *events.Envelope) { m.Producer = "identity" }, func(m *events.Envelope) { m.Name = "files.deleted.v1" }, func(m *events.Envelope) { m.AccountID = randomID(t) }, func(m *events.Envelope) { m.Version = 3 }, func(m *events.Envelope) { m.Data = json.RawMessage(`{"file_id":"bad"}`) }} {
		bad := m
		change(&bad)
		if err := receive.Handle(ctx, bad); err == nil {
			t.Fatal("invalid integration fact accepted")
		}
	}
	dependency := capability.ErrMissing
	for _, access := range []notificationAccess{{err: dependency}, {allowed: false}} {
		h := query.ListInbox{Store: s, Access: access}
		_, err := h.Handle(ctx, app.ListInput{Scope: scope, Limit: 20})
		want := error(app.ErrForbidden)
		if access.err != nil {
			want = dependency
		}
		if !errors.Is(err, want) {
			t.Fatal("dependency converted to denial", err)
		}
	}
}

// The existing Files HTTP acceptance calls this after real upload completion.
func notificationsHTTPAcceptance(t *testing.T, h harness, account, file, token, other string) {
	t.Helper()
	path := "/v1/accounts/" + account + "/notifications"
	preferences := "/v1/accounts/" + account + "/notification-preferences"
	var page struct {
		Items []struct {
			ID, FileID string
			ReadAt     *string `json:"read_at"`
		} `json:"items"`
		UnreadCount uint64 `json:"unread_count"`
	}
	deadline := time.Now().Add(3 * time.Second)
	for {
		status, _, wire := h.request("GET", path, token, "", nil)
		if status != 200 {
			t.Fatal(status, string(wire))
		}
		if json.Unmarshal(wire, &page) != nil {
			t.Fatal(string(wire))
		}
		if len(page.Items) == 1 {
			if !strings.Contains(string(wire), file) {
				t.Fatal("wrong file")
			}
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("notification not delivered", string(wire))
		}
		time.Sleep(10 * time.Millisecond)
	}
	if page.UnreadCount != 1 || page.Items[0].ReadAt != nil {
		t.Fatal("initial read state")
	}
	readPath := path + "/" + page.Items[0].ID + "/read"
	for _, endpoint := range []string{path, preferences} {
		if status, _, _ := h.request("GET", endpoint, other, "", nil); status != 403 {
			t.Fatal("foreign account", status)
		}
	}
	if status, _, _ := h.request("POST", readPath, other, "", nil); status != 403 {
		t.Fatal("foreign read", status)
	}
	status, _, first := h.request("POST", readPath, token, "", nil)
	if status != 200 {
		t.Fatal(status, string(first))
	}
	status, _, repeat := h.request("POST", readPath, token, "", nil)
	if status != 200 || string(first) != string(repeat) {
		t.Fatal("read not settled")
	}
	if status, _, wire := h.request("GET", path+"?unread=true", token, "", nil); status != 200 || !strings.Contains(string(wire), `"items":[]`) || !strings.Contains(string(wire), `"unread_count":0`) {
		t.Fatal(status, string(wire))
	}
	for _, body := range []string{`{}`, `{"media_in_app":null}`, `{"media_in_app":false,"extra":1}`} {
		if status, _, _ := h.request("PUT", preferences, token, body, nil); status != 400 {
			t.Fatal("bad preference accepted", status)
		}
	}
	if status, _, wire := h.request("PUT", preferences, token, `{"media_in_app":false}`, nil); status != 200 || !strings.Contains(string(wire), `"media_in_app":false`) {
		t.Fatal(status, string(wire))
	}
	if status, _, wire := h.request("GET", preferences, token, "", nil); status != 200 || !strings.Contains(string(wire), `"media_in_app":false`) {
		t.Fatal(status, string(wire))
	}
	for _, tail := range []string{"?unread=1", "?limit=0", "?limit=01", "?cursor=bad", "?unread=true&unread=false", "?unknown=1"} {
		if status, _, _ := h.request("GET", path+tail, token, "", nil); status != 400 {
			t.Fatal("bad query accepted", tail, status)
		}
	}
	if status, _, _ := h.request("POST", readPath, token, "", map[string]string{"Idempotency-Key": "ignored"}); status != 400 {
		t.Fatal("unsupported key accepted", status)
	}
	if status, _, _ := h.request("GET", path, "", "", nil); status != 401 {
		t.Fatal("unauthenticated inbox", status)
	}
}
