package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type CancelPushJob struct {
	Store app.PushJobs
	Clock clock.Clock
}

func (h CancelPushJob) Handle(ctx context.Context, actor app.InstallationActor, installation, identity id.ID) (domain.PushJob, error) {
	if err := actor.Validate(h.Clock.Now()); err != nil {
		return domain.PushJob{}, err
	}
	owner := app.PushJobOwner{ID: identity, InstallationID: installation, PrincipalID: actor.Principal, SessionID: actor.Session}
	if err := owner.Validate(); err != nil {
		return domain.PushJob{}, err
	}
	return h.Store.CancelPushJob(ctx, owner, h.Clock.Now())
}

type ReplacePushJob struct {
	Store   app.PushJobs
	Targets app.PushTargets
	Clock   clock.Clock
}

func (h ReplacePushJob) Handle(ctx context.Context, lease app.PushLease, source, identity id.ID, acceptDuplicate bool) (domain.PushJob, error) {
	if lease.Validate() != nil || !source.Valid() || !identity.Valid() || source == identity {
		return domain.PushJob{}, app.ErrInput
	}
	candidate, err := domain.NewPushJob(domain.PushJobSnapshot{ID: identity, InstallationID: lease.InstallationID, PrincipalID: lease.PrincipalID, SessionID: lease.SessionID, InstallationVersion: lease.Version, RetryOf: source, DuplicateRiskAccepted: acceptDuplicate}, h.Clock.Now())
	if err != nil {
		return domain.PushJob{}, err
	}
	prior, found, err := h.Store.FindPushJob(ctx, identity)
	if err != nil {
		return domain.PushJob{}, err
	}
	if found {
		if !prior.SameIntent(candidate) {
			return domain.PushJob{}, app.ErrJobReplay
		}
		return prior, nil
	}
	if _, err := h.Targets.PushTarget(ctx, lease, h.Clock.Now()); err != nil {
		return domain.PushJob{}, err
	}
	return h.Store.ReplacePushJob(ctx, candidate)
}
