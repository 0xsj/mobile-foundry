// Package jobs owns cooperative serial polling, not business retry/delivery policy.
package jobs

import (
	"context"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"sync"
	"time"
)

var ErrInvalid = apperrors.New(apperrors.Invalid, "jobs.invalid", "invalid polling worker lifecycle or configuration")

type Worker struct {
	Run      func(context.Context) error
	Interval time.Duration
	OnError  func(error)
	mu       sync.Mutex
	cancel   context.CancelFunc
	done     chan struct{}
	closed   bool
}

func (w *Worker) Start() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.closed || w.done != nil || w.Run == nil || w.Interval < 10*time.Millisecond || w.Interval > time.Minute {
		return ErrInvalid
	}
	ctx, cancel := context.WithCancel(context.Background())
	w.cancel = cancel
	w.done = make(chan struct{})
	go func() {
		defer close(w.done)
		ticker := time.NewTicker(w.Interval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				if ctx.Err() != nil {
					return
				}
				if err := w.Run(ctx); err != nil && w.OnError != nil {
					w.OnError(err)
				}
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
