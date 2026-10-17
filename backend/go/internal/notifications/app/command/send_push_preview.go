package command

import (
	"context"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

// SendPushPreview is a trusted internal, one-attempt use case, not a public send
// endpoint, fanout worker or durable receipt. A repeated invocation may send twice.
type SendPushPreview struct {
	Targets  app.PushTargets
	Sessions app.SessionEligibility
	Gateway  app.PushGateway
	Clock    clock.Clock
}

func (h SendPushPreview) Handle(ctx context.Context, lease app.PushLease, previewID id.ID) (app.PushAttempt, error) {
	if err := lease.Validate(); err != nil {
		return app.PushAttempt{}, err
	}
	if !previewID.Valid() {
		return app.PushAttempt{}, app.ErrInput
	}
	ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	address, err := h.Targets.PushTarget(ctx, lease, h.Clock.Now())
	if err != nil {
		return app.PushAttempt{}, err
	}
	eligible, err := h.Sessions.SessionEligible(ctx, lease.PrincipalID, lease.SessionID)
	if err != nil {
		return app.PushAttempt{}, err
	}
	if !eligible {
		return app.PushAttempt{}, app.ErrPushSession
	}
	// Identity and Notifications own separate stores. Recheck binding/expiry after
	// the foreign call. The remaining check/send race cannot be made atomic here.
	if _, err := h.Targets.PushTarget(ctx, lease, h.Clock.Now()); err != nil {
		return app.PushAttempt{}, err
	}
	if err := ctx.Err(); err != nil {
		return app.PushAttempt{}, err
	}
	disposition, err := h.Gateway.SendPreview(ctx, address, previewID)
	if err != nil {
		return app.PushAttempt{}, err
	}
	switch disposition {
	case app.PushAccepted:
		return app.PushAttempt{Disposition: disposition}, nil
	case app.PushInvalidTarget:
		retired, err := h.Targets.RetirePushTarget(ctx, lease, h.Clock.Now())
		return app.PushAttempt{Disposition: disposition, Retired: retired}, err
	default:
		return app.PushAttempt{}, app.ErrPushResponse
	}
}
