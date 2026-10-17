package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type GetPushJob struct {
	Store app.PushJobs
	Clock clock.Clock
}

func (h GetPushJob) Handle(ctx context.Context, actor app.InstallationActor, installation, identity id.ID) (domain.PushJob, error) {
	if err := actor.Validate(h.Clock.Now()); err != nil {
		return domain.PushJob{}, err
	}
	if !installation.Valid() || !identity.Valid() {
		return domain.PushJob{}, app.ErrInput
	}
	job, found, err := h.Store.FindPushJob(ctx, identity)
	if err != nil {
		return domain.PushJob{}, err
	}
	s := job.Snapshot()
	if !found || s.InstallationID != installation || s.PrincipalID != actor.Principal || s.SessionID != actor.Session {
		return domain.PushJob{}, app.ErrMissing
	}
	return job, nil
}
