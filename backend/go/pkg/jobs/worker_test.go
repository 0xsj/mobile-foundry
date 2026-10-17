package jobs

import (
	"context"
	"errors"
	"sync/atomic"
	"testing"
	"time"
)

func TestWorkerSerialCancellationAndLifecycle(t *testing.T) {
	entered, exited := make(chan struct{}), make(chan struct{})
	var calls atomic.Int64
	worker := &Worker{Interval: 10 * time.Millisecond, Run: func(ctx context.Context) error {
		if calls.Add(1) != 1 {
			t.Error("overlapping/repeated run")
		}
		close(entered)
		<-ctx.Done()
		close(exited)
		return ctx.Err()
	}}
	if err := worker.Start(); err != nil {
		t.Fatal(err)
	}
	select {
	case <-entered:
	case <-time.After(time.Second):
		t.Fatal("no tick")
	}
	if err := worker.Start(); !errors.Is(err, ErrInvalid) {
		t.Fatal("double start")
	}
	if err := worker.Close(); err != nil {
		t.Fatal(err)
	}
	select {
	case <-exited:
	default:
		t.Fatal("close did not drain")
	}
	if err := worker.Close(); err != nil {
		t.Fatal(err)
	}
	if err := worker.Start(); !errors.Is(err, ErrInvalid) {
		t.Fatal("restarted closed worker")
	}
	invalid := &Worker{}
	if err := invalid.Start(); !errors.Is(err, ErrInvalid) {
		t.Fatal("invalid worker admitted")
	}
}
