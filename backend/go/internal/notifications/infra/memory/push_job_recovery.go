package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"time"
)

func (s *Store) CancelPushJob(ctx context.Context, owner app.PushJobOwner, now time.Time) (domain.PushJob, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.PushJob{}, err
	}
	if err := owner.Validate(); err != nil {
		return domain.PushJob{}, err
	}
	prior, found := s.pushJobs[owner.ID]
	if !found || !owner.Matches(prior) {
		return domain.PushJob{}, app.ErrMissing
	}
	next, err := prior.Cancel(now)
	if err != nil {
		return domain.PushJob{}, err
	}
	s.pushJobs[owner.ID] = next
	return next, nil
}
func (s *Store) ReplacePushJob(ctx context.Context, candidate domain.PushJob) (domain.PushJob, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.PushJob{}, err
	}
	v := candidate.Snapshot()
	if _, err := domain.RestorePushJob(v); err != nil || !v.RetryOf.Valid() || v.Status != domain.JobQueued || v.Attempts != 0 {
		return domain.PushJob{}, app.ErrInput
	}
	if prior, found := s.pushJobs[v.ID]; found {
		if !prior.SameIntent(candidate) {
			return domain.PushJob{}, app.ErrJobReplay
		}
		return prior, nil
	}
	parent, found := s.pushJobs[v.RetryOf]
	owner := app.PushJobOwner{ID: v.RetryOf, InstallationID: v.InstallationID, PrincipalID: v.PrincipalID, SessionID: v.SessionID}
	if !found || !owner.Matches(parent) {
		return domain.PushJob{}, app.ErrMissing
	}
	next, err := parent.Replacement(candidate)
	if err != nil {
		return domain.PushJob{}, err
	}
	for _, job := range s.pushJobs {
		if job.Snapshot().RetryOf == v.RetryOf {
			return domain.PushJob{}, app.ErrJobReplacement
		}
	}
	if len(s.pushJobs) >= 10000 {
		return domain.PushJob{}, app.ErrCapacity
	}
	s.pushJobs[v.ID] = next
	return next, nil
}
func (s *Store) FindPushJobReplacement(ctx context.Context, owner app.PushJobOwner) (domain.PushJob, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.PushJob{}, false, err
	}
	if err := owner.Validate(); err != nil {
		return domain.PushJob{}, false, err
	}
	for _, job := range s.pushJobs {
		if job.Snapshot().RetryOf == owner.ID && owner.Owns(job) {
			return job, true, nil
		}
	}
	return domain.PushJob{}, false, nil
}
