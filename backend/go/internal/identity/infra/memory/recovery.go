package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

func (s *Store) Contact(ctx context.Context, actor id.ID) (domain.Contact, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Contact{}, false, err
	}
	c, ok := s.contacts[actor]
	return c, ok, nil
}
func (s *Store) ByEmail(ctx context.Context, email string) (domain.Principal, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Principal{}, false, err
	}
	for actor, c := range s.contacts {
		if c.Email == email {
			return s.principals[actor], true, nil
		}
	}
	return domain.Principal{}, false, nil
}
func (s *Store) BeginChallenge(ctx context.Context, c domain.Challenge) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := c.Validate(); err != nil {
		return err
	}
	p, ok := s.principals[c.PrincipalID]
	if !ok || p.Snapshot().PasswordHash != c.CredentialHash || c.CreatedAt.Before(p.Snapshot().CreatedAt) {
		return domain.ErrCredential
	}
	if contact, ok := s.contacts[c.PrincipalID]; ok && contact.Email != c.Email {
		return app.ErrContact
	}
	if c.Purpose == domain.ResetPassword {
		if contact, ok := s.contacts[c.PrincipalID]; !ok || contact.Email != c.Email {
			return app.ErrChallenge
		}
	}
	s.challenges[c.Purpose+"/"+c.PrincipalID.String()] = c
	return nil
}
func (s *Store) challenge(digest, purpose string, now time.Time) (domain.Challenge, string, error) {
	for key, c := range s.challenges {
		if c.Digest == digest && c.Purpose == purpose && c.Active(now) {
			p, ok := s.principals[c.PrincipalID]
			if ok && p.Snapshot().PasswordHash == c.CredentialHash {
				return c, key, nil
			}
		}
	}
	return domain.Challenge{}, "", app.ErrChallenge
}
func (s *Store) VerifyEmail(ctx context.Context, digest string, now time.Time, m events.Metadata) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	c, key, err := s.challenge(digest, domain.VerifyEmail, now)
	if err != nil {
		return err
	}
	if contact, ok := s.contacts[c.PrincipalID]; ok && contact.Email != c.Email {
		return app.ErrContact
	}
	for actor, contact := range s.contacts {
		if contact.Email == c.Email && actor != c.PrincipalID {
			return app.ErrContact
		}
	}
	contact := domain.Contact{PrincipalID: c.PrincipalID, Email: c.Email, VerifiedAt: domain.Time(now)}
	if err := contact.Validate(); err != nil {
		return err
	}
	version := s.revisions[c.PrincipalID] + 1
	message, err := s.prepare(ctx, domain.Fact{PrincipalID: c.PrincipalID, Action: "email.verified", Version: version, OccurredAt: domain.Time(now)}, m)
	if err != nil {
		return err
	}
	s.contacts[c.PrincipalID] = contact
	s.revisions[c.PrincipalID] = version
	delete(s.challenges, key)
	s.append(message)
	return nil
}
func (s *Store) ResetPassword(ctx context.Context, digest, hash string, now time.Time, m events.Metadata) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	c, _, err := s.challenge(digest, domain.ResetPassword, now)
	if err != nil {
		return err
	}
	return s.change(ctx, c.PrincipalID, c.CredentialHash, hash, now, m, "password_reset")
}
func (s *Store) ChangePassword(ctx context.Context, actor id.ID, expected, hash string, now time.Time, m events.Metadata) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.change(ctx, actor, expected, hash, now, m, "password_change")
}
func (s *Store) change(ctx context.Context, actor id.ID, expected, hash string, now time.Time, m events.Metadata, reason string) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	p, ok := s.principals[actor]
	if !ok || p.Snapshot().PasswordHash != expected || now.Before(p.Snapshot().CreatedAt) {
		return domain.ErrCredential
	}
	next, err := p.WithPassword(hash)
	if err != nil {
		return err
	}
	revision := s.revisions[actor] + 1
	action := "password.changed"
	if reason == "password_reset" {
		action = "password.recovered"
	}
	changed, err := s.prepare(ctx, domain.Fact{PrincipalID: actor, Action: action, Version: revision, OccurredAt: domain.Time(now)}, m)
	if err != nil {
		return err
	}
	messages := []events.Envelope{changed}
	updates := make(map[id.ID]domain.Session)
	seen := map[id.ID]bool{changed.ID: true}
	for identity, current := range s.sessions {
		if current.Snapshot().PrincipalID != actor || !current.Snapshot().RevokedAt.IsZero() {
			continue
		}
		revoked, err := current.Revoke(now)
		if err != nil {
			return err
		}
		message, err := s.prepare(ctx, revoked.Fact("session.revoked", reason), m)
		if err != nil {
			return err
		}
		if seen[message.ID] {
			return domain.ErrState
		}
		seen[message.ID] = true
		messages = append(messages, message)
		updates[identity] = revoked
	}
	if len(s.outbox)+len(messages) > 10000 {
		return app.ErrCapacity
	}
	s.principals[actor] = next
	s.revisions[actor] = revision
	for identity, next := range updates {
		s.sessions[identity] = next
	}
	for key, c := range s.challenges {
		if c.PrincipalID == actor {
			delete(s.challenges, key)
		}
	}
	for _, message := range messages {
		s.append(message)
	}
	return nil
}
func (s *Store) IssueChecked(ctx context.Context, session domain.Session, expected string, m events.Metadata) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	p, ok := s.principals[session.Snapshot().PrincipalID]
	if !ok || p.Snapshot().PasswordHash != expected {
		return domain.ErrCredential
	}
	return s.issue(ctx, session, m)
}
func (s *Store) PruneChallenges(ctx context.Context, now time.Time, limit int) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return 0, err
	}
	if limit < 1 || limit > 1000 {
		return 0, domain.ErrInput
	}
	n := 0
	for key, c := range s.challenges {
		if !now.Before(c.ExpiresAt) {
			delete(s.challenges, key)
			n++
			if n == limit {
				break
			}
		}
	}
	return n, nil
}
