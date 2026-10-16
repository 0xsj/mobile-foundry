package memory

import (
	"context"
	"sync"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	producer "github.com/0xsj/mobile-foundry/backend/internal/account/infra/events"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const MaxRecords = 10000

type delivery struct {
	message         events.Envelope
	attempts        int
	delivered, dead bool
	failureCode     string
}
type Store struct {
	mu       sync.Mutex
	ids      id.Generator
	accounts map[id.ID]domain.Account
	personal map[id.ID]id.ID
	outbox   map[id.ID]delivery
	order    []id.ID
}

func New(ids id.Generator) *Store {
	return &Store{ids: ids, accounts: make(map[id.ID]domain.Account), personal: make(map[id.ID]id.ID), outbox: make(map[id.ID]delivery)}
}
func (s *Store) Get(ctx context.Context, identity id.ID) (domain.Account, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Account{}, false, err
	}
	account, found := s.accounts[identity]
	return account, found, nil
}
func (s *Store) Create(ctx context.Context, account domain.Account, fact domain.Fact, metadata events.Metadata) error {
	return s.commit(ctx, account, 0, fact, metadata)
}
func (s *Store) Rename(ctx context.Context, account domain.Account, expected uint64, fact domain.Fact, metadata events.Metadata) error {
	return s.commit(ctx, account, expected, fact, metadata)
}
func (s *Store) commit(ctx context.Context, account domain.Account, expected uint64, fact domain.Fact, metadata events.Metadata) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	snapshot := account.Snapshot()
	if _, err := domain.Restore(snapshot); err != nil {
		return err
	}
	action := domain.Created
	if expected != 0 {
		action = domain.Renamed
	}
	required, err := account.Fact(action, snapshot.OwnerID)
	if err != nil || required != fact || snapshot.Version != expected+1 {
		return domain.ErrState
	}
	message, err := producer.Message(fact, metadata, s.ids)
	if err != nil {
		return apperrors.New(apperrors.Internal, "account.event_failed", "account event mapping failed").WithCause(err)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	current, exists := s.accounts[snapshot.ID]
	if expected == 0 && exists {
		return app.ErrConflict
	}
	if expected != 0 {
		if !exists {
			return app.ErrNotFound
		}
		prior := current.Snapshot()
		if prior.Version != expected {
			return app.ErrConflict
		}
		if prior.OwnerID != snapshot.OwnerID || prior.CreatedAt != snapshot.CreatedAt || snapshot.UpdatedAt.Before(prior.UpdatedAt) {
			return domain.ErrState
		}
	}
	if _, duplicate := s.outbox[message.ID]; duplicate {
		return domain.ErrState
	}
	if len(s.outbox) >= MaxRecords || (!exists && len(s.accounts) >= 1000) {
		return app.ErrCapacity
	}
	// One lock protects both state and its outbox append: failure above writes neither.
	s.accounts[snapshot.ID] = account
	s.outbox[message.ID] = delivery{message: message.Clone()}
	s.order = append(s.order, message.ID)
	return nil
}
func (s *Store) Pending(ctx context.Context, limit int) ([]events.Envelope, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	result := make([]events.Envelope, 0)
	for _, identity := range s.order {
		record := s.outbox[identity]
		if !record.delivered && !record.dead {
			result = append(result, record.message.Clone())
			if len(result) >= limit {
				break
			}
		}
	}
	return result, nil
}
func (s *Store) Delivered(ctx context.Context, identity id.ID) error {
	return s.mark(ctx, identity, "", 0)
}
func (s *Store) Failed(ctx context.Context, identity id.ID, code string, maximum int) error {
	return s.mark(ctx, identity, code, maximum)
}
func (s *Store) mark(ctx context.Context, identity id.ID, code string, maximum int) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	record, exists := s.outbox[identity]
	if !exists || record.delivered || record.dead {
		return events.ErrInvalid
	}
	if maximum == 0 {
		record.delivered = true
	} else {
		record.attempts++
		record.failureCode = code
		record.dead = record.attempts >= maximum
	}
	s.outbox[identity] = record
	return nil
}
func (s *Store) Ready(ctx context.Context) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	for _, record := range s.outbox {
		if record.dead {
			return apperrors.New(apperrors.Unavailable, "account.delivery_exhausted", "account event delivery exhausted retries").WithPublic("Account event delivery needs attention.")
		}
	}
	return nil
}
