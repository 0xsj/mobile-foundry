package domain

import "time"

// Cancel wins only before sending. It does not retract a provider request.
func (j PushJob) Cancel(now time.Time) (PushJob, error) {
	s := j.state
	if now.IsZero() {
		return PushJob{}, ErrState
	}
	if s.Status == JobCanceled {
		return j, nil
	}
	if (s.Status != JobQueued && s.Status != JobLeased) || now.Before(s.UpdatedAt) {
		return PushJob{}, ErrJobRecovery
	}
	s.Status, s.Code, s.Claim, s.LeaseUntil, s.UpdatedAt = JobCanceled, "user_canceled", "", time.Time{}, now.UTC().Truncate(time.Microsecond)
	return RestorePushJob(s)
}

// Replacement is a new intent; it never rewrites the source's outcome or lease.
func (j PushJob) Replacement(candidate PushJob) (PushJob, error) {
	a, b := j.state, candidate.state
	if b.RetryOf != a.ID || b.ID == a.ID || b.InstallationID != a.InstallationID || b.PrincipalID != a.PrincipalID || b.SessionID != a.SessionID || b.Status != JobQueued || b.Attempts != 0 || b.CreatedAt.Before(a.UpdatedAt) {
		return PushJob{}, ErrJobRecovery
	}
	switch a.Status {
	case JobFailed, JobBlocked, JobExpired, JobCanceled:
		if b.DuplicateRiskAccepted {
			return PushJob{}, ErrJobRecovery
		}
	case JobUncertain:
		if !b.DuplicateRiskAccepted {
			return PushJob{}, ErrJobRecovery
		}
	default:
		return PushJob{}, ErrJobRecovery
	}
	return RestorePushJob(b)
}
