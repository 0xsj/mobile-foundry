package tests

import (
	"context"
	"errors"
	"fmt"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/memory"
	pg "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func jobOwner(job domain.PushJob) app.PushJobOwner {
	v := job.Snapshot()
	return app.PushJobOwner{ID: v.ID, InstallationID: v.InstallationID, PrincipalID: v.PrincipalID, SessionID: v.SessionID}
}

type recoveryAcknowledgementLoss struct{ jobStore }

func (s recoveryAcknowledgementLoss) CancelPushJob(ctx context.Context, owner app.PushJobOwner, now time.Time) (domain.PushJob, error) {
	if _, err := s.jobStore.CancelPushJob(ctx, owner, now); err != nil {
		return domain.PushJob{}, err
	}
	return domain.PushJob{}, errors.New("private cancel acknowledgement lost")
}
func (s recoveryAcknowledgementLoss) ReplacePushJob(ctx context.Context, job domain.PushJob) (domain.PushJob, error) {
	if _, err := s.jobStore.ReplacePushJob(ctx, job); err != nil {
		return domain.PushJob{}, err
	}
	return domain.PushJob{}, errors.New("private replacement acknowledgement lost")
}

// Explicit store fixtures: these set outcomes without asserting any provider delivery.
func recoverySource(t *testing.T, store jobStore, status domain.PushJobStatus, now time.Time) (domain.PushJob, app.InstallationMutation, *pushClock) {
	t.Helper()
	ctx := context.Background()
	job, mutation := queuedJob(t, store, now)
	clock := &pushClock{now}
	if status == domain.JobCanceled {
		if _, err := store.CancelPushJob(ctx, jobOwner(job), now); err != nil {
			t.Fatal(err)
		}
	} else if status == domain.JobExpired {
		clock.now = now.Add(domain.JobLifetime)
		if _, claimed, err := store.ClaimPushJob(ctx, randomID(t), clock.now); err != nil || claimed {
			t.Fatal("expiry fixture", err)
		}
	} else if status != domain.JobQueued {
		attempts := 1
		if status == domain.JobFailed {
			attempts = domain.JobMaxAttempts
		}
		for range attempts {
			claimed, ok, err := store.ClaimPushJob(ctx, randomID(t), clock.now)
			if err != nil || !ok || claimed.Snapshot().ID != job.Snapshot().ID {
				t.Fatal("claim fixture", err)
			}
			token := claimed.Snapshot().Claim
			result := app.PushJobResult{Status: status}
			switch status {
			case domain.JobFailed:
				result.Status, result.Code = domain.JobQueued, "preflight_unavailable"
			case domain.JobBlocked:
				result.Code = "target_ineligible"
			default:
				result.Started = true
				if err := store.BeginPushJob(ctx, job.Snapshot().ID, token, clock.now); err != nil {
					t.Fatal(err)
				}
				if status == domain.JobUncertain {
					result.Code = "provider_unconfirmed"
				}
			}
			if status == domain.JobSending {
				break
			}
			if err := store.FinishPushJob(ctx, job.Snapshot().ID, token, result, clock.now); err != nil {
				t.Fatal(err)
			}
			if status == domain.JobFailed {
				clock.now = jobState(t, store, job.Snapshot().ID).NextAt
			}
		}
	}
	value, found, err := store.FindPushJob(ctx, job.Snapshot().ID)
	if err != nil || !found || value.Snapshot().Status != status {
		t.Fatal("source fixture", status, err)
	}
	clock.now = value.Snapshot().UpdatedAt.Add(time.Second)
	return value, mutation, clock
}
func recoveryConformance(t *testing.T, store jobStore) {
	ctx := context.Background()
	t.Run("unconfirmed_commits_recover_by_same_identity", func(t *testing.T) {
		now := time.Now().UTC().Truncate(time.Microsecond)
		source, mutation := queuedJob(t, store, now)
		clock := &pushClock{now}
		if _, err := (command.CancelPushJob{Store: recoveryAcknowledgementLoss{store}, Clock: clock}).Handle(ctx, mutation.Actor, mutation.ID, source.Snapshot().ID); err == nil {
			t.Fatal("unconfirmed cancel hidden")
		}
		canceled, err := (command.CancelPushJob{Store: store, Clock: clock}).Handle(ctx, mutation.Actor, mutation.ID, source.Snapshot().ID)
		if err != nil || canceled.Snapshot().Status != domain.JobCanceled {
			t.Fatal("cancel recovery", err)
		}
		replace := command.ReplacePushJob{Store: recoveryAcknowledgementLoss{store}, Targets: store, Clock: clock}
		child := randomID(t)
		if _, err := replace.Handle(ctx, app.PushJobLease(source), source.Snapshot().ID, child, false); err == nil {
			t.Fatal("unconfirmed replacement hidden")
		}
		retained, err := replace.Handle(ctx, app.PushJobLease(source), source.Snapshot().ID, child, false)
		if err != nil || retained.Snapshot().ID != child || retained.Snapshot().RetryOf != source.Snapshot().ID {
			t.Fatal("replacement recovery", err)
		}
		if _, err := store.CancelPushJob(ctx, jobOwner(retained), now); err != nil {
			t.Fatal(err)
		}
	})
	t.Run("cancel_fences_unstarted_claim", func(t *testing.T) {
		now := time.Now().UTC().Truncate(time.Microsecond)
		job, _ := queuedJob(t, store, now)
		owner := jobOwner(job)
		claim, ok, err := store.ClaimPushJob(ctx, randomID(t), now)
		if err != nil || !ok {
			t.Fatal(err)
		}
		canceled, err := store.CancelPushJob(ctx, owner, now)
		if err != nil || canceled.Snapshot().Status != domain.JobCanceled {
			t.Fatal("cancel", err)
		}
		again, err := store.CancelPushJob(ctx, owner, now.Add(time.Second))
		if err != nil || again.Snapshot() != canceled.Snapshot() {
			t.Fatal("cancel replay", err)
		}
		if err := store.BeginPushJob(ctx, owner.ID, claim.Snapshot().Claim, now); !errors.Is(err, domain.ErrJobClaim) {
			t.Fatal("canceled claim began", err)
		}
		if err := store.FinishPushJob(ctx, owner.ID, claim.Snapshot().Claim, app.PushJobResult{Status: domain.JobQueued, Code: "preflight_unavailable"}, now); !errors.Is(err, domain.ErrJobClaim) {
			t.Fatal("canceled job requeued", err)
		}
		foreign := owner
		foreign.SessionID = randomID(t)
		if _, err := store.CancelPushJob(ctx, foreign, now); !errors.Is(err, app.ErrMissing) {
			t.Fatal("foreign cancellation", err)
		}
		if _, claimed, err := store.ClaimPushJob(ctx, randomID(t), now.Add(time.Hour)); err != nil || claimed {
			t.Fatal("canceled job recovered", err)
		}
	})
	for _, atMarker := range []bool{false, true} {
		t.Run(fmt.Sprintf("worker_cancel_sending_%v", atMarker), func(t *testing.T) {
			now := time.Now().UTC().Truncate(time.Microsecond)
			job, mutation := queuedJob(t, store, now)
			clock := &pushClock{now}
			entered, release := make(chan struct{}), make(chan struct{})
			var calls atomic.Int64
			hold := func(ctx context.Context) error {
				close(entered)
				select {
				case <-release:
					return nil
				case <-ctx.Done():
					return ctx.Err()
				}
			}
			run := command.RunPushJob{Store: store, IDs: id.Random{}, Send: command.SendPushPreview{Targets: store, Clock: clock, Sessions: eligibleFunc(func(ctx context.Context, _, _ id.ID) (bool, error) {
				if !atMarker {
					return true, hold(ctx)
				}
				return true, nil
			}), Gateway: gatewayFunc(func(ctx context.Context, _ app.PushAddress, _ id.ID) (app.PushDisposition, error) {
				calls.Add(1)
				if atMarker {
					if err := hold(ctx); err != nil {
						return "", err
					}
				}
				return app.PushAccepted, nil
			})}}
			done := make(chan error, 1)
			go func() { done <- run.Handle(ctx) }()
			select {
			case <-entered:
			case <-time.After(3 * time.Second):
				t.Fatal("worker did not reach boundary")
			}
			_, err := (command.CancelPushJob{Store: store, Clock: clock}).Handle(ctx, mutation.Actor, mutation.ID, job.Snapshot().ID)
			close(release)
			runErr := <-done
			if atMarker {
				if !errors.Is(err, domain.ErrJobRecovery) || runErr != nil || calls.Load() != 1 || jobState(t, store, job.Snapshot().ID).Status != domain.JobAccepted {
					t.Fatal("sending cancellation", err, runErr)
				}
			} else {
				if err != nil || !errors.Is(runErr, domain.ErrJobClaim) || calls.Load() != 0 || jobState(t, store, job.Snapshot().ID).Status != domain.JobCanceled {
					t.Fatal("preflight cancellation", err, runErr)
				}
			}
		})
	}
	for _, status := range []domain.PushJobStatus{domain.JobFailed, domain.JobBlocked, domain.JobExpired, domain.JobCanceled, domain.JobUncertain, domain.JobAccepted, domain.JobRejected, domain.JobQueued, domain.JobSending} {
		t.Run("replacement_"+string(status), func(t *testing.T) {
			source, mutation, clock := recoverySource(t, store, status, time.Now().UTC().Truncate(time.Microsecond))
			before := source.Snapshot()
			lease := app.PushJobLease(source)
			replace := command.ReplacePushJob{Store: store, Targets: store, Clock: clock}
			identity := randomID(t)
			accept := status == domain.JobUncertain
			if accept {
				if _, err := replace.Handle(ctx, lease, before.ID, identity, false); !errors.Is(err, domain.ErrJobRecovery) {
					t.Fatal("uncertain replacement lacked acknowledgement", err)
				}
			}
			if status == domain.JobBlocked {
				mutation.Expected = 1
				mutation.RegistrationID = randomID(t)
				mutation.Spec.Token = "new-target-" + randomID(t).String()
				if _, err := store.RegisterInstallation(ctx, mutation, clock.now); err != nil {
					t.Fatal(err)
				}
				lease.Version = 2
			}
			child, err := replace.Handle(ctx, lease, before.ID, identity, accept)
			refused := status == domain.JobAccepted || status == domain.JobRejected || status == domain.JobQueued || status == domain.JobSending
			if refused {
				if !errors.Is(err, domain.ErrJobRecovery) {
					t.Fatal("ineligible source replaced", status, err)
				}
				if status == domain.JobQueued {
					_, _ = store.CancelPushJob(ctx, jobOwner(source), clock.now)
				}
				if status == domain.JobSending {
					if err := store.FinishPushJob(ctx, before.ID, before.Claim, app.PushJobResult{Started: true, Status: domain.JobUncertain, Code: "provider_unconfirmed"}, clock.now); err != nil {
						t.Fatal(err)
					}
				}
				return
			}
			if err != nil || child.Snapshot().RetryOf != before.ID || child.Snapshot().DuplicateRiskAccepted != accept || child.Snapshot().InstallationVersion != lease.Version {
				t.Fatal("replacement", err)
			}
			if jobState(t, store, before.ID) != before {
				t.Fatal("source outcome changed")
			}
			found, exists, err := store.FindPushJobReplacement(ctx, jobOwner(source))
			if err != nil || !exists || found.Snapshot() != child.Snapshot() {
				t.Fatal("replacement lookup", err)
			}
			again, err := replace.Handle(ctx, lease, before.ID, identity, accept)
			if err != nil || again.Snapshot() != child.Snapshot() {
				t.Fatal("replacement replay", err)
			}
			if _, err := replace.Handle(ctx, lease, before.ID, randomID(t), accept); !errors.Is(err, app.ErrJobReplacement) {
				t.Fatal("two replacements", err)
			}
			if _, err := replace.Handle(ctx, lease, before.ID, identity, !accept); !errors.Is(err, app.ErrJobReplay) {
				t.Fatal("acknowledgement changed", err)
			}
			if _, err := (command.QueuePushPreview{Store: store, Targets: store, Clock: clock}).Handle(ctx, lease, identity); !errors.Is(err, app.ErrJobReplay) {
				t.Fatal("root enqueue erased lineage", err)
			}
			foreign := jobOwner(source)
			foreign.PrincipalID = randomID(t)
			if _, found, err := store.FindPushJobReplacement(ctx, foreign); err != nil || found {
				t.Fatal("foreign replacement leak", err)
			}
			if _, err := store.CancelPushJob(ctx, jobOwner(child), clock.now); err != nil {
				t.Fatal(err)
			}
			settled, err := replace.Handle(ctx, lease, before.ID, identity, accept)
			if err != nil || settled.Snapshot().Status != domain.JobCanceled {
				t.Fatal("settled replay reset child", err)
			}
			// Child owners and source ownership are never silently switched by replacement.
			other, otherMutation := queuedJob(t, store, clock.now)
			otherLease := app.PushJobLease(other)
			if _, err := replace.Handle(ctx, otherLease, before.ID, randomID(t), accept); !errors.Is(err, app.ErrMissing) {
				t.Fatal("foreign source replaced", err)
			}
			_, _ = store.CancelPushJob(ctx, app.PushJobOwner{ID: other.Snapshot().ID, InstallationID: otherMutation.ID, PrincipalID: otherMutation.Actor.Principal, SessionID: otherMutation.Actor.Session}, clock.now)
		})
	}
	t.Run("concurrent_replacement_ids_have_one_winner", func(t *testing.T) {
		source, _, clock := recoverySource(t, store, domain.JobCanceled, time.Now().UTC().Truncate(time.Microsecond))
		lease := app.PushJobLease(source)
		replace := command.ReplacePushJob{Store: store, Targets: store, Clock: clock}
		var wins atomic.Int64
		var group sync.WaitGroup
		for range 12 {
			group.Go(func() {
				if _, err := replace.Handle(ctx, lease, source.Snapshot().ID, randomID(t), false); err == nil {
					wins.Add(1)
				} else if !errors.Is(err, app.ErrJobReplacement) {
					t.Error(err)
				}
			})
		}
		group.Wait()
		if wins.Load() != 1 {
			t.Fatal("replacement contention", wins.Load())
		}
		child, found, err := store.FindPushJobReplacement(ctx, jobOwner(source))
		if err != nil || !found {
			t.Fatal(err)
		}
		_, _ = store.CancelPushJob(ctx, jobOwner(child), clock.now)
		canceled, cancel := context.WithCancel(ctx)
		cancel()
		if _, err := store.CancelPushJob(canceled, jobOwner(child), clock.now); !errors.Is(err, context.Canceled) {
			t.Fatal("canceled operation", err)
		}
		expiredActor := app.InstallationActor{Principal: lease.PrincipalID, Session: lease.SessionID, ExpiresAt: clock.now}
		if _, err := (query.GetPushJobReplacement{Store: store, Clock: clock}).Handle(ctx, expiredActor, lease.InstallationID, source.Snapshot().ID); !errors.Is(err, app.ErrInstallationSession) {
			t.Fatal("expired actor", err)
		}
	})
}
func TestPushJobRecoveryProviders(t *testing.T) {
	t.Run("memory", func(t *testing.T) { recoveryConformance(t, memory.New()) })
	if url := os.Getenv("TEST_POSTGRES_URL"); url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		t.Run("postgres", func(t *testing.T) {
			pool, err := storage.Open(context.Background(), storage.Config{URL: url})
			if err != nil {
				t.Fatal(err)
			}
			defer pool.Close()
			recoveryConformance(t, pg.New(pool))
		})
	}
}
