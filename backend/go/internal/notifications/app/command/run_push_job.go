package command

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

type RunPushJob struct {
	Store app.PushJobs
	Send  SendPushPreview
	IDs   id.Generator
}
type markedGateway struct {
	run     RunPushJob
	job     domain.PushJob
	started bool
}

func (g *markedGateway) SendPreview(ctx context.Context, address app.PushAddress, preview id.ID) (app.PushDisposition, error) {
	v := g.job.Snapshot()
	if err := g.run.Store.BeginPushJob(ctx, v.ID, v.Claim, g.run.Send.Clock.Now()); err != nil {
		return "", err
	}
	// Only a confirmed Begin permits an external effect. Unknown commit => no send.
	g.started = true
	return g.run.Send.Gateway.SendPreview(ctx, address, preview)
}
func (h RunPushJob) Handle(ctx context.Context) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	claim, err := h.IDs.NewID()
	if err != nil {
		return err
	}
	job, claimed, err := h.Store.ClaimPushJob(ctx, claim, h.Send.Clock.Now())
	if err != nil || !claimed {
		return err
	}
	gateway := &markedGateway{run: h, job: job}
	send := h.Send
	send.Gateway = gateway
	result, sendErr := send.Handle(ctx, app.PushJobLease(job), job.Snapshot().ID)
	finish := app.PushJobResult{Started: gateway.started}
	if !gateway.started {
		if errors.Is(sendErr, app.ErrPushTarget) || errors.Is(sendErr, app.ErrPushSession) {
			finish.Status, finish.Code = domain.JobBlocked, "target_ineligible"
		} else {
			finish.Status, finish.Code = domain.JobQueued, "preflight_unavailable"
		}
	} else if result.Disposition == app.PushAccepted && sendErr == nil {
		finish.Status = domain.JobAccepted
	} else if result.Disposition == app.PushInvalidTarget {
		finish.Status, finish.Retired = domain.JobRejected, result.Retired
		if sendErr != nil {
			finish.Code = "cleanup_unconfirmed"
		}
	} else {
		finish.Status, finish.Code = domain.JobUncertain, "provider_unconfirmed"
	}
	// Shutdown still settles an in-flight attempt when possible. Never resend to retry acknowledgement.
	settle, cancel := context.WithTimeout(context.WithoutCancel(ctx), 2*time.Second)
	defer cancel()
	return h.Store.FinishPushJob(settle, job.Snapshot().ID, claim, finish, h.Send.Clock.Now())
}
