// Package broadcast supplies bounded, coalesced ephemeral invalidations. It stores no business data.
package broadcast

import (
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"sync"
)

var ErrCapacity = apperrors.New(apperrors.RateLimited, "broadcast.capacity", "subscription capacity exhausted")
var ErrClosed = apperrors.New(apperrors.Unavailable, "broadcast.closed", "broadcast is stopping")

type subscriber struct {
	owner, key string
	dirty      chan struct{}
}
type Hub struct {
	mu            sync.Mutex
	entries       map[*subscriber]struct{}
	max, perOwner int
	closed        bool
}

func New(max, perOwner int) *Hub {
	return &Hub{entries: make(map[*subscriber]struct{}), max: max, perOwner: perOwner}
}
func (h *Hub) Watch(key, owner string) (<-chan struct{}, func(), error) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.closed {
		return nil, nil, ErrClosed
	}
	count := 0
	for s := range h.entries {
		if s.owner == owner {
			count++
		}
	}
	if key == "" || owner == "" || len(h.entries) >= h.max || count >= h.perOwner {
		return nil, nil, ErrCapacity
	}
	s := &subscriber{key: key, owner: owner, dirty: make(chan struct{}, 1)}
	h.entries[s] = struct{}{}
	return s.dirty, func() {
		h.mu.Lock()
		defer h.mu.Unlock()
		if _, ok := h.entries[s]; ok {
			delete(h.entries, s)
			close(s.dirty)
		}
	}, nil
}
func (h *Hub) Publish(key string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for s := range h.entries {
		if s.key == key {
			select {
			case s.dirty <- struct{}{}:
			default:
			}
		}
	}
}
func (h *Hub) Close() error {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.closed = true
	for s := range h.entries {
		delete(h.entries, s)
		close(s.dirty)
	}
	return nil
}
