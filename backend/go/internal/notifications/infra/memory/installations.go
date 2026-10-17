package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/records"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

type sessionKey struct{ principal, session id.ID }

func (s *Store) installationActor(a app.InstallationActor, now time.Time) error {
	if err := a.Validate(now); err != nil {
		return err
	}
	if s.revoked[sessionKey{a.Principal, a.Session}] {
		return app.ErrInstallationSession
	}
	return nil
}
func (s *Store) installation(identity id.ID, proof string) (domain.Installation, error) {
	i, ok := s.installations[identity]
	if !ok || !records.ProofMatches(i.Snapshot().ProofDigest, proof) {
		return domain.Installation{}, app.ErrInstallationMissing
	}
	return i, nil
}
func (s *Store) RegisterInstallation(ctx context.Context, m app.InstallationMutation, now time.Time) (domain.Installation, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Installation{}, err
	}
	if err := m.Validate(now); err != nil {
		return domain.Installation{}, err
	}
	if err := s.installationActor(m.Actor, now); err != nil {
		return domain.Installation{}, err
	}
	var next domain.Installation
	var err error
	if prior, ok := s.installations[m.ID]; ok {
		v := prior.Snapshot()
		if !records.ProofMatches(v.ProofDigest, m.ProofDigest) {
			return next, app.ErrInstallationMissing
		}
		if m.Expected == 0 || v.Version != m.Expected || v.RegistrationID == m.RegistrationID {
			return next, app.ErrInstallationConflict
		}
		next, err = prior.Bind(m.Actor.Principal, m.Actor.Session, m.RegistrationID, m.Spec, now, m.Actor.ExpiresAt)
	} else {
		if m.Expected != 0 {
			return next, app.ErrInstallationMissing
		}
		if len(s.installations) >= 10000 {
			return next, app.ErrCapacity
		}
		next, err = domain.NewInstallation(m.ID, m.Actor.Principal, m.Actor.Session, m.RegistrationID, m.ProofDigest, m.Spec, now, m.Actor.ExpiresAt)
	}
	if err != nil {
		return next, err
	}
	// At most one other row owns a target. Expired targets are reclaimed on
	// successful registration, never by a timer or a foreign session-store read.
	var retired *domain.Installation
	for identity, prior := range s.installations {
		v := prior.Snapshot()
		if identity == m.ID || !v.Enabled || records.InstallationTarget(v.Spec) != records.InstallationTarget(m.Spec) {
			continue
		}
		if now.Before(v.ExpiresAt) {
			return domain.Installation{}, app.ErrInstallationTarget
		}
		old, err := prior.Disable(now)
		if err != nil {
			return domain.Installation{}, err
		}
		retired = &old
		break
	}
	if retired != nil {
		s.installations[retired.Snapshot().ID] = *retired
	}
	s.installations[m.ID] = next
	return next, nil
}
func (s *Store) GetInstallation(ctx context.Context, a app.InstallationActor, identity id.ID, proof string, now time.Time) (domain.Installation, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Installation{}, err
	}
	if err := s.installationActor(a, now); err != nil {
		return domain.Installation{}, err
	}
	return s.installation(identity, proof)
}
func (s *Store) UnregisterInstallation(ctx context.Context, a app.InstallationActor, identity id.ID, proof string, expected uint64, now time.Time) (domain.Installation, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.Installation{}, err
	}
	if err := s.installationActor(a, now); err != nil {
		return domain.Installation{}, err
	}
	prior, err := s.installation(identity, proof)
	if err != nil {
		return prior, err
	}
	v := prior.Snapshot()
	if v.PrincipalID != a.Principal || v.SessionID != a.Session {
		return domain.Installation{}, app.ErrInstallationBinding
	}
	if !v.Enabled {
		return prior, nil
	}
	if v.Version != expected {
		return domain.Installation{}, app.ErrInstallationConflict
	}
	next, err := prior.Disable(now)
	if err != nil {
		return next, err
	}
	s.installations[identity] = next
	return next, nil
}
func (s *Store) RetireSession(ctx context.Context, r app.SessionRevocation) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := r.Validate(); err != nil {
		return err
	}
	hash := records.RevocationFingerprint(r)
	if prior, ok := s.installationInbox[r.EventID]; ok {
		if prior != hash {
			return app.ErrReplay
		}
		return nil
	}
	if len(s.installationInbox) >= 10000 || len(s.revoked) >= 10000 {
		return app.ErrCapacity
	}
	next := map[id.ID]domain.Installation{}
	for identity, prior := range s.installations {
		v := prior.Snapshot()
		if v.PrincipalID == r.PrincipalID && v.SessionID == r.SessionID {
			i, err := prior.Disable(r.OccurredAt)
			if err != nil {
				return err
			}
			next[identity] = i
		}
	}
	for identity, i := range next {
		s.installations[identity] = i
	}
	s.revoked[sessionKey{r.PrincipalID, r.SessionID}] = true
	s.installationInbox[r.EventID] = hash
	return nil
}
