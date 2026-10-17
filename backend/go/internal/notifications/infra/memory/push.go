package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"time"
)

func (s *Store) PushTarget(ctx context.Context, lease app.PushLease, now time.Time) (app.PushAddress, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return app.PushAddress{}, err
	}
	if err := lease.Validate(); err != nil {
		return app.PushAddress{}, err
	}
	i, ok := s.installations[lease.InstallationID]
	v := i.Snapshot()
	if !ok || !lease.Matches(v) || !v.Enabled || now.Before(v.CreatedAt) || !now.Before(v.ExpiresAt) || s.revoked[sessionKey{lease.PrincipalID, lease.SessionID}] {
		return app.PushAddress{}, app.ErrPushTarget
	}
	return app.PushAddress{Lease: lease, Spec: v.Spec, RegisteredAt: v.UpdatedAt}, nil
}

func (s *Store) RetirePushTarget(ctx context.Context, lease app.PushLease, now time.Time) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return false, err
	}
	if err := lease.Validate(); err != nil {
		return false, err
	}
	i, ok := s.installations[lease.InstallationID]
	if !ok || !lease.Matches(i.Snapshot()) || !i.Snapshot().Enabled {
		return false, nil
	}
	next, err := i.Disable(now)
	if err != nil {
		return false, err
	}
	s.installations[lease.InstallationID] = next
	return true, nil
}
