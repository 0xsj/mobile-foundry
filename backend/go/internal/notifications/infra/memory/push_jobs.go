package memory

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"sort"
	"time"
)

func (s *Store) FindPushJob(ctx context.Context, identity id.ID) (domain.PushJob, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.PushJob{}, false, err
	}
	if !identity.Valid() {
		return domain.PushJob{}, false, app.ErrInput
	}
	job, found := s.pushJobs[identity]
	return job, found, nil
}
func (s *Store) QueuePushJob(ctx context.Context, job domain.PushJob) (domain.PushJob, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.PushJob{}, err
	}
	v := job.Snapshot()
	if _, err := domain.RestorePushJob(v); err != nil || v.Status != domain.JobQueued || v.Attempts != 0 || v.RetryOf != "" {
		return domain.PushJob{}, app.ErrInput
	}
	if prior, ok := s.pushJobs[v.ID]; ok {
		if !prior.SameIntent(job) {
			return domain.PushJob{}, app.ErrJobReplay
		}
		return prior, nil
	}
	if len(s.pushJobs) >= 10000 {
		return domain.PushJob{}, app.ErrCapacity
	}
	s.pushJobs[v.ID] = job
	return job, nil
}
func (s *Store) ClaimPushJob(ctx context.Context, claim id.ID, now time.Time) (domain.PushJob, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return domain.PushJob{}, false, err
	}
	if !claim.Valid() || now.IsZero() {
		return domain.PushJob{}, false, app.ErrInput
	}
	ids := make([]id.ID, 0, len(s.pushJobs))
	for identity, job := range s.pushJobs {
		if job.Due(now) {
			ids = append(ids, identity)
		}
	}
	sort.Slice(ids, func(i, j int) bool {
		a, b := s.pushJobs[ids[i]].Snapshot(), s.pushJobs[ids[j]].Snapshot()
		if a.CreatedAt.Equal(b.CreatedAt) {
			return a.ID < b.ID
		}
		return a.CreatedAt.Before(b.CreatedAt)
	})
	if len(ids) == 0 {
		return domain.PushJob{}, false, nil
	}
	prior := s.pushJobs[ids[0]]
	next, claimed, err := prior.ClaimNext(claim, now)
	if err != nil {
		return domain.PushJob{}, false, err
	}
	s.pushJobs[ids[0]] = next
	return next, claimed, nil
}
func (s *Store) BeginPushJob(ctx context.Context, identity, claim id.ID, now time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	prior, found := s.pushJobs[identity]
	if !found {
		return domain.ErrJobClaim
	}
	next, err := prior.Begin(claim, now)
	if err != nil {
		return err
	}
	s.pushJobs[identity] = next
	return nil
}
func (s *Store) FinishPushJob(ctx context.Context, identity, claim id.ID, result app.PushJobResult, now time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	prior, found := s.pushJobs[identity]
	if !found {
		return domain.ErrJobClaim
	}
	next, err := prior.Finish(claim, result.Started, result.Status, result.Code, result.Retired, now)
	if err != nil {
		return err
	}
	s.pushJobs[identity] = next
	return nil
}
