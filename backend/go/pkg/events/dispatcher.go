package events

import (
	"context"
	"errors"
	"sync"
	"time"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

// Outbox belongs to the producer's store. Its state write and Append are atomic;
// delivery acknowledgment is later and can be lost, so consumers deduplicate.
// Pending may claim fewer than limit records under an owned lease. One dispatcher
// owns each Outbox instance; acknowledgments target that owner's current claim.
type Outbox interface {
	Pending(context.Context, int) ([]Envelope, error)
	Delivered(context.Context, id.ID) error
	Failed(context.Context, id.ID, string, int) error
}
type Dispatcher struct {
	Outbox   Outbox
	Delivery Publisher
	Budget   time.Duration
	mu       sync.Mutex
}

func (d *Dispatcher) Dispatch(ctx context.Context) error {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.Outbox == nil || d.Delivery == nil {
		return ErrInvalid
	}
	budget := d.Budget
	if budget == 0 {
		budget = 2 * time.Second
	}
	if budget < 0 || budget > 2*time.Second {
		return ErrInvalid
	}
	batch, err := d.Outbox.Pending(ctx, 32)
	if err != nil {
		return err
	}
	var failed error
	for _, message := range batch {
		if err := ctx.Err(); err != nil {
			return errors.Join(failed, err)
		}
		attempt, cancel := context.WithTimeout(ctx, budget)
		err := d.Delivery.Publish(attempt, message)
		if attempt.Err() != nil {
			err = attempt.Err()
		}
		cancel()
		if ctx.Err() != nil {
			return errors.Join(failed, ctx.Err())
		}
		if err == nil {
			failed = errors.Join(failed, d.Outbox.Delivered(ctx, message.ID))
			continue
		}
		view := apperrors.Public(err)
		code := view.Code
		if code == "" {
			code = string(view.Kind)
		}
		failed = errors.Join(failed, err, d.Outbox.Failed(ctx, message.ID, code, 5))
	}
	return failed
}

// Worker retries on a bounded polling cadence. Close waits for a cooperative
// in-flight attempt; stores/consumers must honor context cancellation.
type Worker struct {
	dispatcher *Dispatcher
	mu         sync.Mutex
	cancel     context.CancelFunc
	done       chan struct{}
	closed     bool
}

func NewWorker(dispatcher *Dispatcher) *Worker { return &Worker{dispatcher: dispatcher} }
func (w *Worker) Start() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.closed || w.done != nil || w.dispatcher == nil {
		return ErrInvalid
	}
	ctx, cancel := context.WithCancel(context.Background())
	w.cancel = cancel
	w.done = make(chan struct{})
	go func() {
		defer close(w.done)
		ticker := time.NewTicker(100 * time.Millisecond)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				_ = w.dispatcher.Dispatch(ctx) // Outcomes are retained by the producer's outbox.
			}
		}
	}()
	return nil
}
func (w *Worker) Close() error {
	w.mu.Lock()
	w.closed = true
	cancel, done := w.cancel, w.done
	w.mu.Unlock()
	if cancel != nil {
		cancel()
		<-done
	}
	return nil
}
