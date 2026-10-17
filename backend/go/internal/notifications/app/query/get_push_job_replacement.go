package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type GetPushJobReplacement struct {
	Store app.PushJobs
	Clock clock.Clock
}

func (h GetPushJobReplacement) Handle(ctx context.Context, actor app.InstallationActor, installation, identity id.ID) (domain.PushJob, error) {
	if err := actor.Validate(h.Clock.Now()); err != nil {
		return domain.PushJob{}, err
	}
	owner := app.PushJobOwner{ID: identity, InstallationID: installation, PrincipalID: actor.Principal, SessionID: actor.Session}
	if err := owner.Validate(); err != nil {
		return domain.PushJob{}, err
	}
	job, found, err := h.Store.FindPushJobReplacement(ctx, owner)
	if err != nil {
		return domain.PushJob{}, err
	}
	if !found {
		return domain.PushJob{}, app.ErrMissing
	}
	return job, nil
}
