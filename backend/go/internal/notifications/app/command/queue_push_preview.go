package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type QueuePushPreview struct {
	Store   app.PushJobs
	Targets app.PushTargets
	Clock   clock.Clock
}

func (h QueuePushPreview) Handle(ctx context.Context, lease app.PushLease, identity id.ID) (domain.PushJob, error) {
	if lease.Validate() != nil || !identity.Valid() {
		return domain.PushJob{}, app.ErrInput
	}
	job, err := domain.NewPushJob(domain.PushJobSnapshot{ID: identity, InstallationID: lease.InstallationID, PrincipalID: lease.PrincipalID, SessionID: lease.SessionID, InstallationVersion: lease.Version}, h.Clock.Now())
	if err != nil {
		return domain.PushJob{}, err
	}
	prior, found, err := h.Store.FindPushJob(ctx, identity)
	if err != nil {
		return domain.PushJob{}, err
	}
	if found {
		if !prior.SameIntent(job) {
			return domain.PushJob{}, app.ErrJobReplay
		}
		return prior, nil
	}
	if _, err := h.Targets.PushTarget(ctx, lease, h.Clock.Now()); err != nil {
		return domain.PushJob{}, err
	}
	return h.Store.QueuePushJob(ctx, job)
}
