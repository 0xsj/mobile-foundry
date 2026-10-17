package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"sync"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	producer "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/events"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type reference struct {
	session    id.ID
	generation uint64
}
type delivery struct {
	message         events.Envelope
	attempts        int
	delivered, dead bool
}
type Store struct {
	contacts   map[id.ID]domain.Contact
	challenges map[string]domain.Challenge
	revisions  map[id.ID]uint64
	mu         sync.Mutex
	ids        id.Generator
	principals map[id.ID]domain.Principal
	logins     map[string]id.ID
	sessions   map[id.ID]domain.Session
	refresh    map[string]reference
	access     map[string]id.ID
	outbox     map[id.ID]delivery
	order      []id.ID
}

func New(ids id.Generator) *Store {
	return &Store{contacts: make(map[id.ID]domain.Contact), challenges: make(map[string]domain.Challenge), revisions: make(map[id.ID]uint64), ids: ids, principals: map[id.ID]domain.Principal{}, logins: map[string]id.ID{}, sessions: map[id.ID]domain.Session{}, refresh: map[string]reference{}, access: map[string]id.ID{}, outbox: map[id.ID]delivery{}}
}
func (s *Store) ByLogin(ctx context.Context, login string) (domain.Principal, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Principal{}, false, err
	}
	p, found := s.principals[s.logins[login]]
	return p, found, nil
}
func (s *Store) ByID(ctx context.Context, identity id.ID) (domain.Principal, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Principal{}, false, err
	}
	p, found := s.principals[identity]
	return p, found, nil
}
func (s *Store) prepare(ctx context.Context, f domain.Fact, metadata events.Metadata) (events.Envelope, error) {
	if err := ctx.Err(); err != nil {
		return events.Envelope{}, err
	}
	if len(s.outbox) >= 10000 {
		return events.Envelope{}, app.ErrCapacity
	}
	m, err := producer.Message(f, metadata, s.ids)
	if err != nil {
		return events.Envelope{}, domain.ErrState.WithCause(err)
	}
	if _, exists := s.outbox[m.ID]; exists {
		return events.Envelope{}, domain.ErrState
	}
	return m, nil
}
func (s *Store) append(m events.Envelope) {
	s.outbox[m.ID] = delivery{message: m.Clone()}
	s.order = append(s.order, m.ID)
}
func (s *Store) Register(ctx context.Context, p domain.Principal, metadata events.Metadata) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	v := p.Snapshot()
	if _, err := domain.RestorePrincipal(v); err != nil {
		return err
	}
	if _, exists := s.logins[v.Login]; exists {
		return app.ErrExists
	}
	if _, exists := s.principals[v.ID]; exists {
		return domain.ErrState
	}
	if len(s.principals) >= 1000 {
		return app.ErrCapacity
	}
	m, err := s.prepare(ctx, p.Fact(), metadata)
	if err != nil {
		return err
	}
	s.principals[v.ID], s.logins[v.Login] = p, v.ID
	s.revisions[v.ID] = 1
	s.append(m)
	return nil
}
func (s *Store) Issue(ctx context.Context, session domain.Session, metadata events.Metadata) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.issue(ctx, session, metadata)
}
func (s *Store) issue(ctx context.Context, session domain.Session, metadata events.Metadata) error {
	v := session.Snapshot()
	if _, err := domain.RestoreSession(v); err != nil {
		return err
	}
	if v.Generation != 1 || !v.RevokedAt.IsZero() {
		return domain.ErrState
	}
	if _, found := s.principals[v.PrincipalID]; !found {
		return domain.ErrState
	}
	if _, found := s.sessions[v.ID]; found {
		return domain.ErrState
	}
	if _, found := s.refresh[v.RefreshDigest]; found {
		return domain.ErrState
	}
	if _, found := s.access[v.AccessDigest]; found {
		return domain.ErrState
	}
	if len(s.sessions) >= 10000 || len(s.refresh) >= 100000 {
		return app.ErrCapacity
	}
	m, err := s.prepare(ctx, session.Fact("session.issued", ""), metadata)
	if err != nil {
		return err
	}
	s.sessions[v.ID] = session
	s.access[v.AccessDigest] = v.ID
	s.refresh[v.RefreshDigest] = reference{v.ID, v.Generation}
	s.append(m)
	return nil
}
func (s *Store) Rotate(ctx context.Context, digest, access, refresh string, now time.Time, metadata events.Metadata) (domain.Session, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Session{}, false, err
	}
	r, found := s.refresh[digest]
	if !found {
		return domain.Session{}, false, domain.ErrCredential
	}
	current := s.sessions[r.session]
	if !current.Renewable(now) {
		return domain.Session{}, false, domain.ErrCredential
	}
	v := current.Snapshot()
	reuse := r.generation != v.Generation || digest != v.RefreshDigest
	var next domain.Session
	var err error
	action, reason := "session.renewed", ""
	if reuse {
		next, err = current.Revoke(now)
		action, reason = "session.revoked", "refresh_reuse"
	} else {
		if len(s.refresh) >= 100000 {
			return domain.Session{}, false, app.ErrCapacity
		}
		if _, found := s.refresh[refresh]; found {
			return domain.Session{}, false, domain.ErrState
		}
		if _, found := s.access[access]; found {
			return domain.Session{}, false, domain.ErrState
		}
		next, err = current.Rotate(access, refresh, now)
	}
	if err != nil {
		return domain.Session{}, false, err
	}
	m, err := s.prepare(ctx, next.Fact(action, reason), metadata)
	if err != nil {
		return domain.Session{}, false, err
	}
	s.sessions[v.ID] = next
	delete(s.access, v.AccessDigest)
	if !reuse {
		n := next.Snapshot()
		s.access[n.AccessDigest] = n.ID
		s.refresh[n.RefreshDigest] = reference{n.ID, n.Generation}
	}
	s.append(m)
	return next, reuse, nil
}
func (s *Store) Revoke(ctx context.Context, digest string, now time.Time, metadata events.Metadata) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	r, found := s.refresh[digest]
	if !found {
		return domain.ErrCredential
	}
	current := s.sessions[r.session]
	if !current.Snapshot().RevokedAt.IsZero() {
		return nil
	}
	next, err := current.Revoke(now)
	if err != nil {
		return err
	}
	m, err := s.prepare(ctx, next.Fact("session.revoked", "sign_out"), metadata)
	if err != nil {
		return err
	}
	s.sessions[r.session] = next
	delete(s.access, current.Snapshot().AccessDigest)
	s.append(m)
	return nil
}
func (s *Store) Access(ctx context.Context, digest string, now time.Time) (id.ID, error) {
	actor, err := s.AccessSession(ctx, digest, now)
	return actor.ID, err
}
func (s *Store) AccessSession(ctx context.Context, digest string, now time.Time) (principal.Principal, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return principal.Principal{}, err
	}
	session, found := s.sessions[s.access[digest]]
	if !found || !session.Active(now) {
		return principal.Principal{}, domain.ErrCredential
	}
	v := session.Snapshot()
	return principal.Principal{ID: v.PrincipalID, SessionID: v.ID, SessionExpiresAt: v.ExpiresAt}, nil
}
func (s *Store) Pending(ctx context.Context, limit int) ([]events.Envelope, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if limit < 1 {
		return nil, events.ErrInvalid
	}
	result := []events.Envelope{}
	for _, identity := range s.order {
		r := s.outbox[identity]
		if !r.delivered && !r.dead {
			result = append(result, r.message.Clone())
			if len(result) == limit {
				break
			}
		}
	}
	return result, nil
}
func (s *Store) Delivered(ctx context.Context, identity id.ID) error { return s.mark(ctx, identity, 0) }
func (s *Store) Failed(ctx context.Context, identity id.ID, _ string, max int) error {
	return s.mark(ctx, identity, max)
}
func (s *Store) mark(ctx context.Context, identity id.ID, max int) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	r, found := s.outbox[identity]
	if !found || r.delivered || r.dead {
		return events.ErrInvalid
	}
	if max == 0 {
		r.delivered = true
	} else {
		r.attempts++
		r.dead = r.attempts >= max
	}
	s.outbox[identity] = r
	return nil
}
func (s *Store) Ready(ctx context.Context) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	for _, r := range s.outbox {
		if r.dead {
			return apperrors.New(apperrors.Unavailable, "identity.delivery_exhausted", "identity delivery needs attention")
		}
	}
	return nil
}
