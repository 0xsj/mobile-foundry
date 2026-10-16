package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"sync"
)

type key struct {
	consumer string
	eventID  id.ID
}
type Store struct {
	mu      sync.Mutex
	records []app.Record
	inbox   map[key]domain.Evidence
}

func New() *Store { return &Store{inbox: make(map[key]domain.Evidence)} }
func (s *Store) Record(ctx context.Context, consumer string, entry domain.Entry) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	evidence := entry.Evidence()
	identity := key{consumer, evidence.EventID}
	if prior, exists := s.inbox[identity]; exists {
		if prior != evidence {
			return app.ErrReplay
		}
		return nil
	}
	if consumer == "" {
		return domain.ErrEntry
	}
	if _, err := domain.New(evidence, entry.RecordedAt()); err != nil {
		return err
	}
	if len(s.records) >= 10000 {
		return app.ErrCapacity
	}
	// Evidence append and inbox identity share one commit boundary.
	s.records = append(s.records, app.Record{Sequence: uint64(len(s.records) + 1), Entry: entry})
	s.inbox[identity] = evidence
	return nil
}
func (s *Store) List(ctx context.Context, accountID id.ID, after uint64, limit int) (app.Page, error) {
	return s.list(ctx, accountID, after, limit, false)
}
func (s *Store) ListPrincipal(ctx context.Context, identity id.ID, after uint64, limit int) (app.Page, error) {
	return s.list(ctx, identity, after, limit, true)
}
func (s *Store) list(ctx context.Context, identity id.ID, after uint64, limit int, personal bool) (app.Page, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return app.Page{}, err
	}
	page := app.Page{Records: make([]app.Record, 0)}
	for _, record := range s.records {
		scope := record.Entry.Evidence().AccountID
		if personal {
			scope = record.Entry.Evidence().PrincipalID
		}
		if record.Sequence <= after || scope != identity {
			continue
		}
		if len(page.Records) == limit {
			page.Next = page.Records[len(page.Records)-1].Sequence
			break
		}
		page.Records = append(page.Records, record)
	}
	return page, nil
}
