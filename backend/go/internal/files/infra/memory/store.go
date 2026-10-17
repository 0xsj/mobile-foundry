package memory

import (
	"context"
	"sort"
	"sync"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	producer "github.com/0xsj/mobile-foundry/backend/internal/files/infra/events"
	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/records"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type receiptKey struct {
	account, actor id.ID
	key            string
}
type saved struct {
	hash    [32]byte
	receipt app.Receipt
}
type delivery struct {
	message         events.Envelope
	stream          id.ID
	attempts        int
	delivered, dead bool
	failureCode     string
}
type Store struct {
	mu       sync.Mutex
	ids      id.Generator
	files    map[id.ID]domain.File
	receipts map[receiptKey]saved
	outbox   map[id.ID]delivery
	order    []id.ID
}

func New(ids id.Generator) *Store {
	return &Store{ids: ids, files: map[id.ID]domain.File{}, receipts: map[receiptKey]saved{}, outbox: map[id.ID]delivery{}}
}
func (s *Store) Apply(ctx context.Context, scope app.Scope, c app.Command, candidate id.ID, now time.Time, metadata events.Metadata) (app.Receipt, error) {
	if err := c.Validate(); err != nil {
		return app.Receipt{}, err
	}
	if !scope.Actor.Valid() || !scope.Account.Valid() {
		return app.Receipt{}, app.ErrInput
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return app.Receipt{}, err
	}
	key := receiptKey{scope.Account, scope.Actor, c.Key}
	hash := records.Fingerprint(c)
	if prior, ok := s.receipts[key]; ok && c.Key != "" {
		if prior.hash != hash {
			return app.Receipt{}, app.ErrKey
		}
		return prior.receipt, nil
	}
	var current *domain.File
	if f, ok := s.files[c.FileID]; ok && f.Snapshot().AccountID == scope.Account {
		current = &f
	}
	next, err := app.Plan(scope, c, current, candidate, now)
	if err != nil {
		return app.Receipt{}, err
	}
	snap := next.Snapshot()
	if c.Action == app.Request {
		if _, exists := s.files[snap.ID]; exists {
			return app.Receipt{}, app.ErrConflict
		}
	}
	message, err := producer.Message(next.Fact(scope.Actor), metadata, s.ids)
	if err != nil {
		return app.Receipt{}, err
	}
	if _, exists := s.outbox[message.ID]; exists {
		return app.Receipt{}, domain.ErrState
	}
	if len(s.outbox) >= 10000 || (c.Action == app.Request && len(s.files) >= 1000) {
		return app.Receipt{}, apperrors.New(apperrors.Unavailable, "files.capacity", "memory file metadata capacity reached")
	}
	if err := ctx.Err(); err != nil {
		return app.Receipt{}, err
	}
	r := app.Receipt{Key: c.Key, Action: c.Action, Actor: scope.Actor, File: snap}
	s.files[snap.ID] = next
	s.outbox[message.ID] = delivery{message: message.Clone(), stream: snap.ID}
	s.order = append(s.order, message.ID)
	if c.Key != "" {
		s.receipts[key] = saved{hash, r}
	}
	return r, nil
}
func (s *Store) Receipt(ctx context.Context, scope app.Scope, key string) (app.Receipt, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return app.Receipt{}, false, err
	}
	r, ok := s.receipts[receiptKey{scope.Account, scope.Actor, key}]
	return r.receipt, ok, nil
}
func (s *Store) Replay(ctx context.Context, scope app.Scope, c app.Command) (app.Receipt, bool, error) {
	if err := c.Validate(); err != nil {
		return app.Receipt{}, false, err
	}
	if c.Key == "" {
		return app.Receipt{}, false, nil
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return app.Receipt{}, false, err
	}
	r, ok := s.receipts[receiptKey{scope.Account, scope.Actor, c.Key}]
	if ok && r.hash != records.Fingerprint(c) {
		return app.Receipt{}, false, app.ErrKey
	}
	return r.receipt, ok, nil
}
func (s *Store) Get(ctx context.Context, account, file id.ID) (domain.File, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.File{}, false, err
	}
	f, ok := s.files[file]
	if !ok || f.Snapshot().AccountID != account {
		return domain.File{}, false, nil
	}
	return f, true, nil
}
func (s *Store) List(ctx context.Context, account, after id.ID, limit int) ([]domain.File, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if limit < 1 || limit > 101 {
		return nil, app.ErrInput
	}
	rows := []domain.File{}
	for _, f := range s.files {
		s := f.Snapshot()
		if s.AccountID == account && s.ID > after && s.Status != domain.Deleted {
			rows = append(rows, f)
		}
	}
	sort.Slice(rows, func(i, j int) bool { return rows[i].Snapshot().ID < rows[j].Snapshot().ID })
	if len(rows) > limit {
		rows = rows[:limit]
	}
	return rows, nil
}

func (s *Store) Pending(ctx context.Context, limit int) ([]events.Envelope, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	result := make([]events.Envelope, 0)
	blocked := map[id.ID]bool{}
	for _, identity := range s.order {
		record := s.outbox[identity]
		if record.delivered || blocked[record.stream] {
			continue
		}
		blocked[record.stream] = true
		if !record.dead {
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
			return apperrors.New(apperrors.Unavailable, "files.delivery_exhausted", "files event delivery exhausted retries").WithPublic("Files event delivery needs attention.")
		}
	}
	return nil
}
