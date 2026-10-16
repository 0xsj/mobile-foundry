package memory

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"sync"
	"testing"
	"time"
)

func account(t *testing.T) domain.Account {
	t.Helper()
	a, err := domain.New(id.ID("00000000-0000-4000-8000-000000000001"), id.ID("00000000-0000-4000-8000-000000000002"), "Original", time.Date(2026, 10, 13, 0, 0, 0, 0, time.UTC))
	if err != nil {
		t.Fatal(err)
	}
	return a
}

type brokenIDs struct{}

func (brokenIDs) NewID() (id.ID, error) { return "", errors.New("generator unavailable") }
func TestAtomicStateOutboxAndCompareAndSwap(t *testing.T) {
	ctx := context.Background()
	a := account(t)
	fact, _ := a.Fact(domain.Created, a.Snapshot().OwnerID)
	broken := New(brokenIDs{})
	if broken.Create(ctx, a, fact, events.Metadata{}) == nil {
		t.Fatal("mapping failure ignored")
	}
	if _, found, _ := broken.Get(ctx, a.Snapshot().ID); found {
		t.Fatal("state committed without event")
	}
	s := New(id.Random{})
	if err := s.Create(ctx, a, fact, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	var group sync.WaitGroup
	results := make(chan error, 2)
	for _, name := range []string{"One", "Two"} {
		group.Add(1)
		go func(name string) {
			defer group.Done()
			next, _ := a.Rename(name, a.Snapshot().UpdatedAt.Add(time.Second))
			fact, _ := next.Fact(domain.Renamed, a.Snapshot().OwnerID)
			results <- s.Rename(ctx, next, 1, fact, events.Metadata{})
		}(name)
	}
	group.Wait()
	close(results)
	successes, conflicts := 0, 0
	for err := range results {
		if err == nil {
			successes++
		} else if errors.Is(err, app.ErrConflict) {
			conflicts++
		} else {
			t.Fatal(err)
		}
	}
	pending, _ := s.Pending(ctx, 32)
	if successes != 1 || conflicts != 1 || len(pending) != 2 {
		t.Fatal("CAS/state/event invariant failed", successes, conflicts, len(pending))
	}
	pending[0].Data[0] = '['
	again, _ := s.Pending(ctx, 32)
	if again[0].Data[0] != '{' {
		t.Fatal("outbox snapshot aliased store")
	}
	current, _, _ := s.Get(ctx, a.Snapshot().ID)
	forged := current.Snapshot()
	forged.OwnerID = id.ID("00000000-0000-4000-8000-000000000003")
	forged.Version++
	candidate, _ := domain.Restore(forged)
	forgedFact, _ := candidate.Fact(domain.Renamed, forged.OwnerID)
	if s.Rename(ctx, candidate, 2, forgedFact, events.Metadata{}) == nil {
		t.Fatal("store accepted owner replacement")
	}
	for range 5 {
		if err := s.Failed(ctx, again[0].ID, "unavailable", 5); err != nil {
			t.Fatal(err)
		}
	}
	if s.Ready(ctx) == nil {
		t.Fatal("exhausted delivery remained ready")
	}
}
func TestCancellationAndInvalidFactsWriteNothing(t *testing.T) {
	a := account(t)
	fact, _ := a.Fact(domain.Created, a.Snapshot().OwnerID)
	s := New(id.Random{})
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if !errors.Is(s.Create(ctx, a, fact, events.Metadata{}), context.Canceled) {
		t.Fatal("cancellation ignored")
	}
	fact.Version = 2
	if s.Create(context.Background(), a, fact, events.Metadata{}) == nil {
		t.Fatal("inconsistent fact admitted")
	}
	pending, _ := s.Pending(context.Background(), 32)
	if len(pending) != 0 {
		t.Fatal("failed command appended event")
	}
}

func TestBootstrapMappingRollsBackWithEventFailure(t *testing.T) {
	a := account(t)
	fact, _ := a.Fact(domain.Created, a.Snapshot().OwnerID)
	s := New(brokenIDs{})
	if _, _, err := s.Bootstrap(context.Background(), a, fact, events.Metadata{}); err == nil {
		t.Fatal("failed creation event was ignored")
	}
	if len(s.personal) != 0 || len(s.accounts) != 0 || len(s.outbox) != 0 {
		t.Fatal("failed bootstrap left partial state")
	}
	s.ids = id.Random{}
	if _, created, err := s.Bootstrap(context.Background(), a, fact, events.Metadata{}); err != nil || !created {
		t.Fatal("bootstrap could not recover after a failed append", err)
	}
}
