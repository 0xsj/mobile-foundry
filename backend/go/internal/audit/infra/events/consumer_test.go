package events

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"sync"
	"testing"
	"time"
)

func TestConsumerAdmissionReplayAndScope(t *testing.T) {
	store := memory.New()
	receive := Subscribe(command.Record{Store: store, Clock: clock.System{}}).Handle
	identity := id.ID("00000000-0000-4000-8000-000000000001")
	message := events.Envelope{ID: identity, Name: "account.created.v1", Producer: "account", AccountID: identity, ActorID: identity, Version: 1, OccurredAt: time.Date(2026, 10, 13, 0, 0, 0, 0, time.UTC), Data: []byte(`{"account_id":"00000000-0000-4000-8000-000000000001","version":1}`)}
	var group sync.WaitGroup
	for range 8 {
		group.Add(1)
		go func() {
			defer group.Done()
			if err := receive(context.Background(), message); err != nil {
				t.Error(err)
			}
		}()
	}
	group.Wait()
	page, _ := store.List(context.Background(), identity, 0, 100)
	if len(page.Records) != 1 {
		t.Fatal("concurrent replay duplicated evidence")
	}
	changed := message
	changed.ActorID = id.ID("00000000-0000-4000-8000-000000000002")
	if !errors.Is(receive(context.Background(), changed), app.ErrReplay) {
		t.Fatal("event ID reuse with different evidence accepted")
	}
	for _, payload := range []string{`null`, `{"account_id":"00000000-0000-4000-8000-000000000001","version":2}`, `{"account_id":"00000000-0000-4000-8000-000000000001","version":1,"name":"secret"}`} {
		invalid := message
		invalid.Data = []byte(payload)
		if receive(context.Background(), invalid) == nil {
			t.Fatal("invalid event admitted", payload)
		}
	}
	other := id.ID("00000000-0000-4000-8000-000000000003")
	page, _ = store.List(context.Background(), other, 0, 100)
	if len(page.Records) != 0 {
		t.Fatal("cross-account projection")
	}
}
