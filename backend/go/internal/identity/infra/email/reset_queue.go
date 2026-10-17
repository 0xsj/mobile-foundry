package email

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"sync"
	"time"
)

type ResetRequester interface {
	Handle(context.Context, string) error
}

// ResetQueue admits addresses independently of existence. SMTP and lookup happen
// after the public response, avoiding a synchronous mail-delivery timing oracle.
// It is bounded and ephemeral; accepted does not promise durable delivery.
type ResetQueue struct {
	request ResetRequester
	failure func(error)
	queue   chan string
	mu      sync.Mutex
	cancel  context.CancelFunc
	done    chan struct{}
	closed  bool
}

func NewResetQueue(request ResetRequester, failure func(error)) *ResetQueue {
	return &ResetQueue{request: request, failure: failure, queue: make(chan string, 64)}
}
func (q *ResetQueue) Handle(ctx context.Context, email string) error {
	address, err := domain.Email(email)
	if err != nil {
		return err
	}
	email = address
	q.mu.Lock()
	defer q.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	if q.closed || q.done == nil {
		return app.ErrMail
	}
	select {
	case q.queue <- email:
		return nil
	default:
		return app.ErrBusy
	}
}
func (q *ResetQueue) Start() error {
	q.mu.Lock()
	defer q.mu.Unlock()
	if q.closed || q.done != nil || q.request == nil {
		return app.ErrMail
	}
	ctx, cancel := context.WithCancel(context.Background())
	q.cancel = cancel
	q.done = make(chan struct{})
	go func() {
		defer close(q.done)
		for {
			select {
			case <-ctx.Done():
				return
			case email := <-q.queue:
				if ctx.Err() != nil {
					return
				}
				budget, done := context.WithTimeout(ctx, 10*time.Second)
				err := q.request.Handle(budget, email)
				done()
				if err != nil && q.failure != nil {
					q.failure(err)
				}
			}
		}
	}()
	return nil
}
func (q *ResetQueue) Close() error {
	q.mu.Lock()
	q.closed = true
	cancel, done := q.cancel, q.done
	q.mu.Unlock()
	if cancel != nil {
		cancel()
		<-done
	}
	return nil
}
