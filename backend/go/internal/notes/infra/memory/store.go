package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"sort"
	"sync"
)

type recordKey struct{ account, note id.ID }
type receiptKey struct{ account, actor, command id.ID }
type committed struct {
	input   domain.Write
	receipt domain.Receipt
}
type Store struct {
	mutex    sync.Mutex
	notes    map[recordKey]domain.Note
	receipts map[receiptKey]committed
}

func New() *Store {
	return &Store{notes: make(map[recordKey]domain.Note), receipts: make(map[receiptKey]committed)}
}
func (s *Store) Apply(ctx context.Context, account, actor id.ID, w domain.Write, candidate id.ID) (domain.Receipt, error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Receipt{}, err
	}
	if !account.Valid() || !actor.Valid() {
		return domain.Receipt{}, id.ErrInvalid
	}
	if err := w.Validate(); err != nil {
		return domain.Receipt{}, err
	}
	key := receiptKey{account, actor, w.CommandID}
	if previous, ok := s.receipts[key]; ok {
		if previous.input != w {
			return domain.Receipt{}, domain.ErrKey
		}
		return previous.receipt, nil
	}
	var current *domain.Note
	if n, ok := s.notes[recordKey{account, w.NoteID}]; ok {
		current = &n
	}
	r, err := domain.Decide(w, current, candidate, account, actor)
	if err != nil {
		return r, err
	}
	target := recordKey{account, r.NoteID}
	if w.Action == domain.Create {
		if _, ok := s.notes[target]; ok {
			return domain.Receipt{}, domain.ErrConflict
		}
	}
	if w.Action == domain.Delete {
		delete(s.notes, target)
	} else {
		s.notes[target] = domain.Note{ID: r.NoteID, Title: r.Title, Version: r.Version}
	}
	s.receipts[key] = committed{w, r}
	return r, nil
}
func (s *Store) Receipt(ctx context.Context, account, actor, command id.ID) (domain.Receipt, bool, error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Receipt{}, false, err
	}
	v, ok := s.receipts[receiptKey{account, actor, command}]
	return v.receipt, ok, nil
}
func (s *Store) Get(ctx context.Context, account, note id.ID) (domain.Note, bool, error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Note{}, false, err
	}
	n, ok := s.notes[recordKey{account, note}]
	return n, ok, nil
}
func (s *Store) List(ctx context.Context, account, after id.ID, limit int) ([]domain.Note, error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if limit < 1 || limit > 101 {
		return nil, domain.ErrWrite
	}
	result := make([]domain.Note, 0)
	for k, n := range s.notes {
		if k.account == account && k.note > after {
			result = append(result, n)
		}
	}
	sort.Slice(result, func(i, j int) bool { return result[i].ID < result[j].ID })
	if len(result) > limit {
		result = result[:limit]
	}
	return result, nil
}
