package email

import (
	"context"
	"testing"
	"time"
)

type resetAction func(context.Context, string) error

func (f resetAction) Handle(ctx context.Context, address string) error { return f(ctx, address) }
func TestResetQueueRespondsWithoutLookupAndBoundsPendingWork(t *testing.T) {
	started := make(chan struct{})
	q := NewResetQueue(resetAction(func(ctx context.Context, _ string) error { close(started); <-ctx.Done(); return ctx.Err() }), nil)
	if err := q.Start(); err != nil {
		t.Fatal(err)
	}
	defer q.Close()
	if err := q.Handle(context.Background(), "user@example.test"); err != nil {
		t.Fatal(err)
	}
	select {
	case <-started:
	case <-time.After(time.Second):
		t.Fatal("worker did not start")
	}
	for range 64 {
		if err := q.Handle(context.Background(), "unknown@example.test"); err != nil {
			t.Fatal("queue refused before capacity", err)
		}
	}
	if err := q.Handle(context.Background(), "known@example.test"); err == nil {
		t.Fatal("unbounded recovery work")
	}
	if err := q.Close(); err != nil {
		t.Fatal(err)
	}
	if err := q.Handle(context.Background(), "user@example.test"); err == nil {
		t.Fatal("closed queue accepted request")
	}
}
