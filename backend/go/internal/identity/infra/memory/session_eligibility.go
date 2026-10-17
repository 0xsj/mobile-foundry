package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

func (s *Store) SessionEligible(ctx context.Context, principal, session id.ID, now time.Time) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return false, err
	}
	v, found := s.sessions[session]
	return found && v.Snapshot().PrincipalID == principal && v.Renewable(now), nil
}
