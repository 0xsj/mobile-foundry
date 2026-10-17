package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/records"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"sort"
	"sync"
	"time"
)

type Store struct {
	pushJobs          map[id.ID]domain.PushJob
	mu                sync.Mutex
	installations     map[id.ID]domain.Installation
	revoked           map[sessionKey]bool
	installationInbox map[id.ID]string
	inbox             map[id.ID]string
	items             map[id.ID]domain.Item
	disabled          map[app.Scope]bool
	sequence          map[app.Scope]uint64
}

func New() *Store {
	return &Store{pushJobs: map[id.ID]domain.PushJob{}, installations: map[id.ID]domain.Installation{}, revoked: map[sessionKey]bool{}, installationInbox: map[id.ID]string{}, inbox: map[id.ID]string{}, items: map[id.ID]domain.Item{}, disabled: map[app.Scope]bool{}, sequence: map[app.Scope]uint64{}}
}
func (s *Store) Deliver(ctx context.Context, d app.Delivery, at time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := d.Validate(); err != nil {
		return err
	}
	if at.IsZero() {
		return app.ErrInput
	}
	hash := records.Fingerprint(d)
	if prior, ok := s.inbox[d.EventID]; ok {
		if prior != hash {
			return app.ErrReplay
		}
		return nil
	}
	if len(s.inbox) >= 10000 {
		return app.ErrCapacity
	}
	scope := app.Scope{Actor: d.PrincipalID, Account: d.AccountID}
	if !s.disabled[scope] {
		item, err := d.Item(s.sequence[scope]+1, at)
		if err != nil {
			return err
		}
		s.items[d.EventID] = item
		s.sequence[scope]++
	}
	s.inbox[d.EventID] = hash
	return nil
}
func (s *Store) List(ctx context.Context, input app.ListInput) (app.Page, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return app.Page{}, err
	}
	if err := input.Validate(); err != nil {
		return app.Page{}, err
	}
	page := app.Page{Items: []domain.Item{}}
	for _, item := range s.items {
		v := item.Snapshot()
		if v.AccountID != input.Scope.Account || v.PrincipalID != input.Scope.Actor {
			continue
		}
		if v.ReadAt.IsZero() {
			page.UnreadCount++
		}
		if input.Before != 0 && v.Sequence >= input.Before || input.UnreadOnly && !v.ReadAt.IsZero() {
			continue
		}
		page.Items = append(page.Items, item)
	}
	sort.Slice(page.Items, func(i, j int) bool { return page.Items[i].Snapshot().Sequence > page.Items[j].Snapshot().Sequence })
	if len(page.Items) > input.Limit {
		page.Items = page.Items[:input.Limit]
		page.Next = page.Items[input.Limit-1].Snapshot().Sequence
	}
	return page, nil
}
func (s *Store) MarkRead(ctx context.Context, scope app.Scope, identity id.ID, at time.Time) (domain.Item, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Item{}, err
	}
	if !scope.Valid() || !identity.Valid() || at.IsZero() {
		return domain.Item{}, app.ErrInput
	}
	item, ok := s.items[identity]
	v := item.Snapshot()
	if !ok || v.AccountID != scope.Account || v.PrincipalID != scope.Actor {
		return domain.Item{}, app.ErrMissing
	}
	next, err := item.MarkRead(at)
	if err != nil {
		return domain.Item{}, err
	}
	s.items[identity] = next
	return next, nil
}
func (s *Store) MediaEnabled(ctx context.Context, scope app.Scope) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return false, err
	}
	if !scope.Valid() {
		return false, app.ErrInput
	}
	return !s.disabled[scope], nil
}
func (s *Store) SetMediaEnabled(ctx context.Context, scope app.Scope, enabled bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	if !scope.Valid() {
		return app.ErrInput
	}
	if !enabled && !s.disabled[scope] && len(s.disabled) >= 10000 {
		return app.ErrCapacity
	}
	if enabled {
		delete(s.disabled, scope)
	} else {
		s.disabled[scope] = true
	}
	return nil
}
