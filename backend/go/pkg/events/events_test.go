package events

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"reflect"
	"testing"
	"time"
)

func message() Envelope {
	return Envelope{ID: id.ID("00000000-0000-4000-8000-000000000001"), Name: "account.created.v1", Producer: "account", AccountID: id.ID("00000000-0000-4000-8000-000000000002"), ActorID: id.ID("00000000-0000-4000-8000-000000000003"), Version: 1, OccurredAt: time.Date(2026, 10, 13, 0, 0, 0, 0, time.UTC), Data: []byte(`{"version":1}`)}
}
func TestIndependentConsumersAndDetachedEnvelopes(t *testing.T) {
	bus := NewMemory()
	var seen []string
	failed := errors.New("consumer unavailable")
	_ = bus.Register(Subscription{Consumer: "audit.account", Names: []string{"account.created.v1"}, Handle: func(_ context.Context, m Envelope) error {
		seen = append(seen, "audit")
		m.Data[0] = '['
		return failed
	}})
	_ = bus.Register(Subscription{Consumer: "realtime.account", Names: []string{"account.created.v1"}, Handle: func(_ context.Context, m Envelope) error {
		seen = append(seen, "realtime")
		if m.Data[0] != '{' {
			t.Fatal("consumer mutated another consumer's payload")
		}
		return nil
	}})
	bus.Freeze()
	original := message()
	if err := bus.Publish(context.Background(), original); !errors.Is(err, failed) || original.Data[0] != '{' || !reflect.DeepEqual(seen, []string{"audit", "realtime"}) {
		t.Fatal("independent delivery failed", err)
	}
	if bus.Register(Subscription{}) == nil {
		t.Fatal("late invalid registration accepted")
	}
	invalid := original
	invalid.ActorID = "spoofed"
	if bus.Publish(context.Background(), invalid) == nil {
		t.Fatal("invalid envelope delivered")
	}
	if !errors.Is(NewMemory().Publish(context.Background(), original), ErrNoConsumer) {
		t.Fatal("message without a consumer acknowledged")
	}
}

type outbox struct {
	delivered, attempts int
	dead                bool
}

func (o *outbox) Pending(ctx context.Context, _ int) ([]Envelope, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if o.delivered != 0 || o.dead {
		return nil, nil
	}
	return []Envelope{message()}, nil
}
func (o *outbox) Delivered(context.Context, id.ID) error { o.delivered++; return nil }
func (o *outbox) Failed(_ context.Context, _ id.ID, _ string, maximum int) error {
	o.attempts++
	o.dead = o.attempts >= maximum
	return nil
}

type publisher func(context.Context, Envelope) error

func (p publisher) Publish(ctx context.Context, m Envelope) error { return p(ctx, m) }
func TestDispatcherRetryAcknowledgmentAndExhaustion(t *testing.T) {
	store := &outbox{}
	calls := 0
	d := Dispatcher{Outbox: store, Delivery: publisher(func(context.Context, Envelope) error {
		calls++
		if calls == 1 {
			return errors.New("lost acknowledgment")
		}
		return nil
	})}
	if d.Dispatch(context.Background()) == nil || store.delivered != 0 || store.attempts != 1 {
		t.Fatal("failure acknowledged")
	}
	if d.Dispatch(context.Background()) != nil || store.delivered != 1 || calls != 2 {
		t.Fatal("retry failed")
	}
	store = &outbox{}
	d = Dispatcher{Outbox: store, Delivery: publisher(func(context.Context, Envelope) error { return ErrNoConsumer })}
	for range 7 {
		_ = d.Dispatch(context.Background())
	}
	if !store.dead || store.attempts != 5 {
		t.Fatal("retry bound not enforced")
	}
	store = &outbox{}
	ctx, cancel := context.WithCancel(context.Background())
	d = Dispatcher{Outbox: store, Delivery: publisher(func(context.Context, Envelope) error { cancel(); return nil })}
	if !errors.Is(d.Dispatch(ctx), context.Canceled) || store.delivered != 0 || store.attempts != 0 {
		t.Fatal("cancellation changed delivery state")
	}
}

func TestDeliveryDeadlineCountsTowardRetryBound(t *testing.T) {
	store := &outbox{}
	d := Dispatcher{Outbox: store, Budget: time.Millisecond, Delivery: publisher(func(ctx context.Context, _ Envelope) error { <-ctx.Done(); return nil })}
	for range 6 {
		_ = d.Dispatch(context.Background())
	}
	if store.attempts != 5 || !store.dead || store.delivered != 0 {
		t.Fatal("deadline failure retried without bound or admitted late success")
	}
}
func TestWorkerCloseCancelsAndJoinsDelivery(t *testing.T) {
	started := make(chan struct{})
	stopped := make(chan struct{})
	w := NewWorker(&Dispatcher{Outbox: &outbox{}, Delivery: publisher(func(ctx context.Context, _ Envelope) error {
		close(started)
		<-ctx.Done()
		close(stopped)
		return ctx.Err()
	})})
	if err := w.Start(); err != nil {
		t.Fatal(err)
	}
	select {
	case <-started:
	case <-time.After(time.Second):
		t.Fatal("worker did not start delivery")
	}
	if err := w.Close(); err != nil {
		t.Fatal(err)
	}
	<-stopped
	if w.Start() == nil {
		t.Fatal("closed worker restarted")
	}
	_ = w.Close()
}
