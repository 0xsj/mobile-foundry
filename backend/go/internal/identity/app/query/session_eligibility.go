package query

import (
	"context"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type SessionStatus interface {
	SessionEligible(context.Context, id.ID, id.ID, time.Time) (bool, error)
}

// SessionEligibility checks the absolute session lifetime, not the short access
// token lifetime. This is a trusted module answer, never credential resolution.
type SessionEligibility struct {
	Store SessionStatus
	Clock clock.Clock
}

func (h SessionEligibility) Handle(ctx context.Context, principal, session id.ID) (bool, error) {
	if !principal.Valid() || !session.Valid() {
		return false, domain.ErrInput
	}
	return h.Store.SessionEligible(ctx, principal, session, h.Clock.Now())
}
