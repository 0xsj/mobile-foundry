package events

import (
	"context"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/audit/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func TestIdentityAdmissionAndPrincipalIsolation(t *testing.T) {
	store := memory.New()
	receive := SubscribeIdentity(command.Record{Store: store, Clock: clock.System{}}).Handle
	subject := id.ID("00000000-0000-4000-8000-000000000001")
	message := events.Envelope{ID: subject, Name: "identity.session.revoked.v1", Producer: "identity", ActorID: subject, Version: 3, OccurredAt: time.Date(2026, 10, 13, 0, 0, 0, 0, time.UTC), Data: []byte(`{"principal_id":"00000000-0000-4000-8000-000000000001","session_id":"00000000-0000-4000-8000-000000000002","version":3,"reason":"refresh_reuse"}`)}
	for range 2 {
		if err := receive(context.Background(), message); err != nil {
			t.Fatal(err)
		}
	}
	page, err := store.ListPrincipal(context.Background(), subject, 0, 100)
	if err != nil || len(page.Records) != 1 || page.Records[0].Entry.Evidence().AccountID != "" {
		t.Fatal("scope/replay failed", err)
	}
	for _, data := range []string{`{"principal_id":"00000000-0000-4000-8000-000000000003","session_id":"00000000-0000-4000-8000-000000000002","version":3,"reason":"refresh_reuse"}`, `{"principal_id":"00000000-0000-4000-8000-000000000001","version":3}`, `{"principal_id":"00000000-0000-4000-8000-000000000001","session_id":"00000000-0000-4000-8000-000000000002","version":3,"reason":"refresh_reuse","password":"private"}`} {
		invalid := message
		invalid.Data = []byte(data)
		if receive(context.Background(), invalid) == nil {
			t.Fatal("invalid identity fact admitted")
		}
	}
	other := id.ID("00000000-0000-4000-8000-000000000003")
	page, err = store.ListPrincipal(context.Background(), other, 0, 100)
	if err != nil || len(page.Records) != 0 {
		t.Fatal("principal evidence leaked", err)
	}
	invalid := message
	invalid.AccountID = subject
	if receive(context.Background(), invalid) == nil {
		t.Fatal("fabricated account scope admitted")
	}
}
