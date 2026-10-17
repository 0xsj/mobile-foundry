package app

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

var ErrJobReplay = apperrors.New(apperrors.Conflict, "notifications.push_job_conflict", "push job ID reused for different input")
var ErrJobReplacement = apperrors.New(apperrors.Conflict, "notifications.push_job_replacement_exists", "source job already has a replacement; read it before another action")

type PushJobOwner struct{ ID, InstallationID, PrincipalID, SessionID id.ID }

func (a PushJobOwner) Validate() error {
	if !a.ID.Valid() || !a.InstallationID.Valid() || !a.PrincipalID.Valid() || !a.SessionID.Valid() {
		return ErrInput
	}
	return nil
}
func (a PushJobOwner) Owns(j domain.PushJob) bool {
	s := j.Snapshot()
	return s.InstallationID == a.InstallationID && s.PrincipalID == a.PrincipalID && s.SessionID == a.SessionID
}
func (a PushJobOwner) Matches(j domain.PushJob) bool { return a.ID == j.Snapshot().ID && a.Owns(j) }
func (PushJobOwner) String() string                  { return "notifications.PushJobOwner{redacted}" }
func (PushJobOwner) GoString() string                { return "notifications.PushJobOwner{redacted}" }

type PushJobResult struct {
	Started bool
	Status  domain.PushJobStatus
	Code    string
	Retired bool
}

// PushJobs owns immutable intents and fenced execution. No token/proof is copied here.
type PushJobs interface {
	FindPushJob(context.Context, id.ID) (domain.PushJob, bool, error)
	QueuePushJob(context.Context, domain.PushJob) (domain.PushJob, error)
	ClaimPushJob(context.Context, id.ID, time.Time) (domain.PushJob, bool, error)
	BeginPushJob(context.Context, id.ID, id.ID, time.Time) error
	FinishPushJob(context.Context, id.ID, id.ID, PushJobResult, time.Time) error
	CancelPushJob(context.Context, PushJobOwner, time.Time) (domain.PushJob, error)
	ReplacePushJob(context.Context, domain.PushJob) (domain.PushJob, error)
	FindPushJobReplacement(context.Context, PushJobOwner) (domain.PushJob, bool, error)
}

func PushJobLease(job domain.PushJob) PushLease {
	s := job.Snapshot()
	return PushLease{InstallationID: s.InstallationID, PrincipalID: s.PrincipalID, SessionID: s.SessionID, Version: s.InstallationVersion}
}
