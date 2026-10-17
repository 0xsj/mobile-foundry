package domain

import (
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

type PushJobStatus string

const (
	JobQueued    PushJobStatus = "queued"
	JobLeased    PushJobStatus = "leased"
	JobSending   PushJobStatus = "sending"
	JobAccepted  PushJobStatus = "accepted"
	JobRejected  PushJobStatus = "invalid_target"
	JobBlocked   PushJobStatus = "blocked"
	JobFailed    PushJobStatus = "failed"
	JobUncertain PushJobStatus = "uncertain"
	JobExpired   PushJobStatus = "expired"
	JobCanceled  PushJobStatus = "canceled"
)

var ErrJobClaim = apperrors.New(apperrors.Conflict, "notifications.push_job_claim_lost", "push job claim lost or expired")
var ErrJobRecovery = apperrors.New(apperrors.Conflict, "notifications.push_job_recovery_refused", "job cannot be canceled or replaced with this acknowledgement")

const JobLeaseDuration = 30 * time.Second
const JobLifetime = 15 * time.Minute
const JobMaxAttempts = 5

type PushJobSnapshot struct {
	ID, InstallationID, PrincipalID, SessionID          id.ID
	InstallationVersion                                 uint64
	Status                                              PushJobStatus
	Attempts                                            int
	Claim                                               id.ID
	CreatedAt, UpdatedAt, ExpiresAt, NextAt, LeaseUntil time.Time
	Code                                                string
	Retired                                             bool
	RetryOf                                             id.ID
	DuplicateRiskAccepted                               bool
}

func (PushJobSnapshot) String() string   { return "notifications.PushJobSnapshot{redacted}" }
func (PushJobSnapshot) GoString() string { return "notifications.PushJobSnapshot{redacted}" }

type PushJob struct{ state PushJobSnapshot }

func (PushJob) String() string   { return "notifications.PushJob{redacted}" }
func (PushJob) GoString() string { return "notifications.PushJob{redacted}" }
func NewPushJob(s PushJobSnapshot, now time.Time) (PushJob, error) {
	now = now.UTC().Truncate(time.Microsecond)
	s.Status, s.Attempts, s.Claim = JobQueued, 0, ""
	s.CreatedAt, s.UpdatedAt, s.ExpiresAt, s.NextAt, s.LeaseUntil = now, now, now.Add(JobLifetime), now, time.Time{}
	s.Code, s.Retired = "", false
	return RestorePushJob(s)
}
func RestorePushJob(s PushJobSnapshot) (PushJob, error) {
	s.CreatedAt = s.CreatedAt.UTC().Truncate(time.Microsecond)
	s.UpdatedAt = s.UpdatedAt.UTC().Truncate(time.Microsecond)
	s.ExpiresAt = s.ExpiresAt.UTC().Truncate(time.Microsecond)
	s.NextAt = s.NextAt.UTC().Truncate(time.Microsecond)
	s.LeaseUntil = s.LeaseUntil.UTC().Truncate(time.Microsecond)
	if s.RetryOf != "" && (!s.RetryOf.Valid() || s.RetryOf == s.ID) || s.RetryOf == "" && s.DuplicateRiskAccepted {
		return PushJob{}, ErrState
	}
	if !s.ID.Valid() || !s.InstallationID.Valid() || !s.PrincipalID.Valid() || !s.SessionID.Valid() || s.InstallationVersion < 1 || s.InstallationVersion > 1<<53-1 || s.CreatedAt.IsZero() || s.UpdatedAt.Before(s.CreatedAt) || !s.ExpiresAt.Equal(s.CreatedAt.Add(JobLifetime)) || s.NextAt.Before(s.CreatedAt) || s.Attempts < 0 || s.Attempts > JobMaxAttempts {
		return PushJob{}, ErrState
	}
	active := s.Status == JobLeased || s.Status == JobSending
	switch s.Status {
	case JobQueued, JobLeased, JobSending, JobAccepted, JobRejected, JobBlocked, JobFailed, JobUncertain, JobExpired, JobCanceled:
	default:
		return PushJob{}, ErrState
	}
	if active && (!s.Claim.Valid() || s.LeaseUntil.IsZero() || s.Attempts == 0) || !active && (s.Claim != "" || !s.LeaseUntil.IsZero()) {
		return PushJob{}, ErrState
	}
	if active && !s.LeaseUntil.After(s.UpdatedAt) {
		return PushJob{}, ErrState
	}
	switch s.Code {
	case "", "preflight_unavailable", "attempts_exhausted", "target_ineligible", "provider_unconfirmed", "worker_lost", "cleanup_unconfirmed", "expired", "user_canceled":
	default:
		return PushJob{}, ErrState
	}
	if s.Retired && s.Status != JobRejected {
		return PushJob{}, ErrState
	}
	switch s.Status {
	case JobQueued, JobLeased, JobSending:
		if s.Code != "" && s.Code != "preflight_unavailable" {
			return PushJob{}, ErrState
		}
	case JobAccepted:
		if s.Code != "" || s.Attempts == 0 {
			return PushJob{}, ErrState
		}
	case JobRejected:
		if s.Code != "" && s.Code != "cleanup_unconfirmed" || s.Attempts == 0 {
			return PushJob{}, ErrState
		}
	case JobBlocked:
		if s.Code != "target_ineligible" || s.Attempts == 0 {
			return PushJob{}, ErrState
		}
	case JobFailed:
		if s.Code != "attempts_exhausted" || s.Attempts != JobMaxAttempts {
			return PushJob{}, ErrState
		}
	case JobUncertain:
		if s.Code != "provider_unconfirmed" && s.Code != "worker_lost" || s.Attempts == 0 {
			return PushJob{}, ErrState
		}
	case JobExpired:
		if s.Code != "expired" {
			return PushJob{}, ErrState
		}
	case JobCanceled:
		if s.Code != "user_canceled" {
			return PushJob{}, ErrState
		}
	}
	return PushJob{state: s}, nil
}
func (j PushJob) Snapshot() PushJobSnapshot { return j.state }
func (j PushJob) SameIntent(other PushJob) bool {
	a, b := j.state, other.state
	return a.ID == b.ID && a.InstallationID == b.InstallationID && a.PrincipalID == b.PrincipalID && a.SessionID == b.SessionID && a.InstallationVersion == b.InstallationVersion && a.RetryOf == b.RetryOf && a.DuplicateRiskAccepted == b.DuplicateRiskAccepted
}
func (j PushJob) Due(now time.Time) bool {
	s := j.state
	switch s.Status {
	case JobQueued:
		return !now.Before(s.NextAt) || !now.Before(s.ExpiresAt)
	case JobLeased:
		return !now.Before(s.LeaseUntil) || !now.Before(s.ExpiresAt)
	case JobSending:
		return !now.Before(s.LeaseUntil)
	default:
		return false
	}
}
func (j PushJob) ClaimNext(token id.ID, now time.Time) (PushJob, bool, error) {
	if !token.Valid() || now.Before(j.state.CreatedAt) {
		return PushJob{}, false, ErrState
	}
	if !j.Due(now) {
		return j, false, nil
	}
	s := j.state
	s.UpdatedAt = now.UTC().Truncate(time.Microsecond)
	if s.Status == JobSending {
		s.Status, s.Code = JobUncertain, "worker_lost"
	} else if !now.Before(s.ExpiresAt) {
		s.Status, s.Code = JobExpired, "expired"
	} else if s.Attempts == JobMaxAttempts {
		s.Status, s.Code = JobFailed, "attempts_exhausted"
	} else {
		s.Status, s.Claim, s.LeaseUntil = JobLeased, token, s.UpdatedAt.Add(JobLeaseDuration)
		s.Attempts++
		result, err := RestorePushJob(s)
		return result, true, err
	}
	s.Claim, s.LeaseUntil = "", time.Time{}
	result, err := RestorePushJob(s)
	return result, false, err
}
func (j PushJob) Begin(token id.ID, now time.Time) (PushJob, error) {
	s := j.state
	if s.Status != JobLeased || s.Claim != token || !now.Before(s.LeaseUntil) || !now.Before(s.ExpiresAt) || now.Before(s.UpdatedAt) {
		return PushJob{}, ErrJobClaim
	}
	s.Status, s.UpdatedAt = JobSending, now.UTC().Truncate(time.Microsecond)
	return RestorePushJob(s)
}

// Finish never changes a stale claim. Retry is legal only before Begin committed.
func (j PushJob) Finish(token id.ID, started bool, status PushJobStatus, code string, retired bool, now time.Time) (PushJob, error) {
	s := j.state
	expected := JobLeased
	if started {
		expected = JobSending
	}
	if s.Status != expected || s.Claim != token || !now.Before(s.LeaseUntil) || now.Before(s.UpdatedAt) {
		return PushJob{}, ErrJobClaim
	}
	if started {
		if status != JobAccepted && status != JobRejected && status != JobUncertain {
			return PushJob{}, ErrState
		}
	} else if status != JobQueued && status != JobBlocked {
		return PushJob{}, ErrState
	}
	s.Status, s.Code, s.Retired, s.UpdatedAt = status, code, retired, now.UTC().Truncate(time.Microsecond)
	s.Claim, s.LeaseUntil = "", time.Time{}
	if status == JobQueued {
		if s.Attempts == JobMaxAttempts {
			s.Status, s.Code = JobFailed, "attempts_exhausted"
		} else {
			s.NextAt = s.UpdatedAt.Add(time.Second * time.Duration(1<<uint(s.Attempts-1)))
		}
	}
	return RestorePushJob(s)
}
