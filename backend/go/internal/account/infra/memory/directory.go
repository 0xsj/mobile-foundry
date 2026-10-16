package memory

import (
	"context"
	"slices"
	"strings"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	producer "github.com/0xsj/mobile-foundry/backend/internal/account/infra/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func (s *Store) Bootstrap(ctx context.Context, candidate domain.Account, fact domain.Fact, metadata events.Metadata) (domain.Account, bool, error) {
	if err := ctx.Err(); err != nil {
		return domain.Account{}, false, err
	}
	snapshot := candidate.Snapshot()
	if _, err := domain.Restore(snapshot); err != nil {
		return domain.Account{}, false, err
	}
	required, err := candidate.Fact(domain.Created, snapshot.OwnerID)
	if err != nil || fact != required {
		return domain.Account{}, false, domain.ErrState
	}
	message, err := producer.Message(fact, metadata, s.ids)
	if err != nil {
		return domain.Account{}, false, domain.ErrState.WithCause(err)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Account{}, false, err
	}
	if identity, found := s.personal[snapshot.OwnerID]; found {
		account, exists := s.accounts[identity]
		if !exists || !account.OwnedBy(snapshot.OwnerID) {
			return domain.Account{}, false, domain.ErrState
		}
		return account, false, nil
	}
	if _, exists := s.accounts[snapshot.ID]; exists {
		return domain.Account{}, false, app.ErrConflict
	}
	if _, duplicate := s.outbox[message.ID]; duplicate {
		return domain.Account{}, false, domain.ErrState
	}
	if len(s.accounts) >= 1000 || len(s.outbox) >= MaxRecords {
		return domain.Account{}, false, app.ErrCapacity
	}
	// The mapping, aggregate and fact become visible together under this lock.
	s.accounts[snapshot.ID] = candidate
	s.personal[snapshot.OwnerID] = snapshot.ID
	s.outbox[message.ID] = delivery{message: message.Clone()}
	s.order = append(s.order, message.ID)
	return candidate, true, nil
}

func (s *Store) List(ctx context.Context, owner, after id.ID, limit int) ([]domain.Account, error) {
	if !owner.Valid() || (after != "" && !after.Valid()) || limit < 1 || limit > 101 {
		return nil, app.ErrInput
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	rows := make([]domain.Account, 0)
	for identity, account := range s.accounts {
		if account.OwnedBy(owner) && identity > after {
			rows = append(rows, account)
		}
	}
	slices.SortFunc(rows, func(a, b domain.Account) int {
		return strings.Compare(a.Snapshot().ID.String(), b.Snapshot().ID.String())
	})
	if len(rows) > limit {
		rows = rows[:limit]
	}
	return rows, nil
}
