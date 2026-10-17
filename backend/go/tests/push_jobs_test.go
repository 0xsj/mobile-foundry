package tests

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/memory"
	pg "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

type jobStore interface {
	pushStore
	app.PushJobs
}
type jobFaults struct {
	jobStore
	unknownBegin, failedFinish, failedCleanup bool
}

func (s jobFaults) BeginPushJob(ctx context.Context, job, claim id.ID, at time.Time) error {
	if err := s.jobStore.BeginPushJob(ctx, job, claim, at); err != nil {
		return err
	}
	if s.unknownBegin {
		return errors.New("commit acknowledgement lost")
	}
	return nil
}
func (s jobFaults) FinishPushJob(ctx context.Context, job, claim id.ID, result app.PushJobResult, at time.Time) error {
	if s.failedFinish {
		return errors.New("settlement unavailable")
	}
	return s.jobStore.FinishPushJob(ctx, job, claim, result, at)
}
func (s jobFaults) RetirePushTarget(ctx context.Context, lease app.PushLease, at time.Time) (bool, error) {
	if s.failedCleanup {
		return false, errors.New("cleanup unavailable")
	}
	return s.jobStore.RetirePushTarget(ctx, lease, at)
}
func queuedJob(t *testing.T, store jobStore, now time.Time) (domain.PushJob, app.InstallationMutation) {
	t.Helper()
	actor := installationActor(t, now)
	mutation := installationMutation(t, actor)
	if _, err := store.RegisterInstallation(context.Background(), mutation, now); err != nil {
		t.Fatal(err)
	}
	lease := app.PushLease{InstallationID: mutation.ID, PrincipalID: actor.Principal, SessionID: actor.Session, Version: 1}
	job, err := (command.QueuePushPreview{Store: store, Targets: store, Clock: &pushClock{now}}).Handle(context.Background(), lease, randomID(t))
	if err != nil {
		t.Fatal(err)
	}
	return job, mutation
}
func jobState(t *testing.T, store app.PushJobs, identity id.ID) domain.PushJobSnapshot {
	t.Helper()
	job, found, err := store.FindPushJob(context.Background(), identity)
	if err != nil || !found {
		t.Fatal("job lookup", err)
	}
	return job.Snapshot()
}
func pushJobConformance(t *testing.T, store jobStore) {
	ctx := context.Background()
	for _, scenario := range []string{"accepted", "invalid_target", "uncertain", "preflight_retry", "preflight_exhausted", "session_denied", "rebound", "unknown_begin", "lost_settlement", "cleanup_failure"} {
		t.Run(scenario, func(t *testing.T) {
			now := time.Now().UTC().Truncate(time.Microsecond)
			clock := &pushClock{now}
			job, mutation := queuedJob(t, store, now)
			identity := job.Snapshot().ID
			lease := app.PushJobLease(job)
			queue := command.QueuePushPreview{Store: store, Targets: store, Clock: clock}
			prior, err := queue.Handle(ctx, lease, identity)
			if err != nil || prior.Snapshot() != job.Snapshot() {
				t.Fatal("enqueue replay", err)
			}
			changed := lease
			changed.Version++
			if _, err := queue.Handle(ctx, changed, identity); !errors.Is(err, app.ErrJobReplay) {
				t.Fatal("conflicting intent", err)
			}
			if scenario == "rebound" {
				mutation.Expected = 1
				mutation.RegistrationID = randomID(t)
				mutation.Actor = installationActor(t, now)
				if _, err := store.RegisterInstallation(ctx, mutation, now); err != nil {
					t.Fatal(err)
				}
			}
			calls := 0
			eligible := scenario != "session_denied"
			outage := scenario == "preflight_retry" || scenario == "preflight_exhausted"
			targetStore := jobFaults{jobStore: store, unknownBegin: scenario == "unknown_begin", failedFinish: scenario == "lost_settlement", failedCleanup: scenario == "cleanup_failure"}
			run := command.RunPushJob{Store: targetStore, IDs: id.Random{}, Send: command.SendPushPreview{Targets: targetStore, Clock: clock, Sessions: eligibleFunc(func(context.Context, id.ID, id.ID) (bool, error) {
				if outage {
					return false, errors.New("dependency unavailable")
				}
				return eligible, nil
			}), Gateway: gatewayFunc(func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error) {
				calls++
				switch scenario {
				case "invalid_target", "cleanup_failure":
					return app.PushInvalidTarget, nil
				case "uncertain":
					return "", errors.New("unknown acknowledgement")
				default:
					return app.PushAccepted, nil
				}
			})}}
			err = run.Handle(ctx)
			if scenario == "unknown_begin" || scenario == "lost_settlement" {
				if err == nil {
					t.Fatal("settlement failure hidden")
				}
			} else if err != nil {
				t.Fatal(err)
			}
			if outage {
				first := jobState(t, store, identity)
				if first.Status != domain.JobQueued || first.Attempts != 1 || calls != 0 {
					t.Fatal("unsafe retry")
				}
				if err := run.Handle(ctx); err != nil {
					t.Fatal(err)
				}
				if jobState(t, store, identity).Attempts != 1 {
					t.Fatal("backoff ignored")
				}
				if scenario == "preflight_retry" {
					outage = false
				}
				for n := 1; n < domain.JobMaxAttempts; n++ {
					clock.now = clock.now.Add(16 * time.Second)
					if err := run.Handle(ctx); err != nil {
						t.Fatal(err)
					}
				}
			}
			if scenario == "unknown_begin" || scenario == "lost_settlement" {
				clock.now = clock.now.Add(domain.JobLeaseDuration + time.Second)
				if err := run.Handle(ctx); err != nil {
					t.Fatal(err)
				}
			}
			state := jobState(t, store, identity)
			want := domain.JobAccepted
			wantCalls := 1
			switch scenario {
			case "invalid_target", "cleanup_failure":
				want = domain.JobRejected
			case "uncertain", "unknown_begin", "lost_settlement":
				want = domain.JobUncertain
			case "session_denied", "rebound":
				want = domain.JobBlocked
				wantCalls = 0
			case "preflight_exhausted":
				want = domain.JobFailed
				wantCalls = 0
			}
			if scenario == "unknown_begin" {
				wantCalls = 0
			}
			if state.Status != want || calls != wantCalls {
				t.Fatalf("result %s calls=%d want %s/%d", state.Status, calls, want, wantCalls)
			}
			if scenario == "cleanup_failure" && state.Code != "cleanup_unconfirmed" {
				t.Fatal("cleanup uncertainty lost")
			}
			if scenario == "invalid_target" && !state.Retired {
				t.Fatal("target not retired")
			}
			// Same immutable intent remains a replay after execution/retirement; it never resets work.
			if _, err := queue.Handle(ctx, lease, identity); err != nil {
				t.Fatal("settled enqueue replay", err)
			}
			if err := run.Handle(ctx); err != nil {
				t.Fatal(err)
			}
			if calls != wantCalls {
				t.Fatal("terminal job resent")
			}
		})
	}
	t.Run("claim_contention_recovery_expiry_and_fencing", func(t *testing.T) {
		now := time.Now().UTC().Truncate(time.Microsecond)
		job, _ := queuedJob(t, store, now)
		identity := job.Snapshot().ID
		var group sync.WaitGroup
		var wins atomic.Int64
		var winner domain.PushJob
		var mu sync.Mutex
		for range 12 {
			group.Go(func() {
				got, claimed, err := store.ClaimPushJob(ctx, randomID(t), now)
				if err != nil {
					t.Error(err)
				}
				if claimed {
					wins.Add(1)
					mu.Lock()
					winner = got
					mu.Unlock()
				}
			})
		}
		group.Wait()
		if wins.Load() != 1 {
			t.Fatal("duplicate claim", wins.Load())
		}
		old := winner.Snapshot().Claim
		next, claimed, err := store.ClaimPushJob(ctx, randomID(t), now.Add(domain.JobLeaseDuration))
		if err != nil || !claimed || next.Snapshot().Attempts != 2 {
			t.Fatal("unstarted claim not recovered", err)
		}
		if err := store.BeginPushJob(ctx, identity, old, now.Add(domain.JobLeaseDuration)); !errors.Is(err, domain.ErrJobClaim) {
			t.Fatal("old worker began", err)
		}
		fresh := next.Snapshot().Claim
		if err := store.BeginPushJob(ctx, identity, fresh, now.Add(domain.JobLeaseDuration)); err != nil {
			t.Fatal(err)
		}
		if _, claimed, err := store.ClaimPushJob(ctx, randomID(t), now.Add(2*domain.JobLeaseDuration)); err != nil || claimed {
			t.Fatal("in-flight effect replayed", err)
		}
		if jobState(t, store, identity).Status != domain.JobUncertain {
			t.Fatal("sending claim not quarantined")
		}
		if err := store.FinishPushJob(ctx, identity, fresh, app.PushJobResult{Started: true, Status: domain.JobAccepted}, now.Add(2*domain.JobLeaseDuration)); !errors.Is(err, domain.ErrJobClaim) {
			t.Fatal("late acknowledgement overwrote recovery")
		}
		expired, _ := queuedJob(t, store, now)
		if _, claimed, err := store.ClaimPushJob(ctx, randomID(t), now.Add(domain.JobLifetime)); err != nil || claimed {
			t.Fatal("expired job claimed", err)
		}
		if jobState(t, store, expired.Snapshot().ID).Status != domain.JobExpired {
			t.Fatal("expiry not retained")
		}
		canceled, cancel := context.WithCancel(ctx)
		cancel()
		if _, _, err := store.ClaimPushJob(canceled, randomID(t), now); !errors.Is(err, context.Canceled) {
			t.Fatal("cancellation lost", err)
		}
	})
}
func TestPushJobProviders(t *testing.T) {
	t.Run("memory", func(t *testing.T) { pushJobConformance(t, memory.New()) })
	if url := os.Getenv("TEST_POSTGRES_URL"); url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		t.Run("postgres", func(t *testing.T) {
			pool, err := storage.Open(context.Background(), storage.Config{URL: url})
			if err != nil {
				t.Fatal(err)
			}
			defer pool.Close()
			pushJobConformance(t, pg.New(pool))
		})
	}
}

func TestPushJobPostgresBeginAndSettlementRollback(t *testing.T) {
	url := os.Getenv("TEST_POSTGRES_URL")
	if url == "" || os.Getenv("TEST_POSTGRES_DISPOSABLE") != "1" {
		t.Skip("disposable PostgreSQL only")
	}
	ctx := context.Background()
	pool, err := storage.Open(ctx, storage.Config{URL: url})
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	store := pg.New(pool)
	for _, phase := range []string{"sending", "accepted"} {
		t.Run(phase, func(t *testing.T) {
			now := time.Now().UTC().Truncate(time.Microsecond)
			clock := &pushClock{now}
			job, _ := queuedJob(t, store, now)
			if _, err := pool.Exec(ctx, `ALTER TABLE notifications.push_jobs ADD CONSTRAINT fail_job_write CHECK(status<>'`+phase+`') NOT VALID`); err != nil {
				t.Fatal(err)
			}
			active := true
			defer func() {
				if active {
					_, _ = pool.Exec(ctx, `ALTER TABLE notifications.push_jobs DROP CONSTRAINT fail_job_write`)
				}
			}()
			calls := 0
			run := command.RunPushJob{Store: store, IDs: id.Random{}, Send: command.SendPushPreview{Targets: store, Clock: clock, Sessions: eligibleFunc(func(context.Context, id.ID, id.ID) (bool, error) { return true, nil }), Gateway: gatewayFunc(func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error) {
				calls++
				return app.PushAccepted, nil
			})}}
			err := run.Handle(ctx)
			state := jobState(t, store, job.Snapshot().ID)
			if phase == "sending" {
				if err != nil || calls != 0 || state.Status != domain.JobQueued {
					t.Fatal("uncommitted marker allowed effect", state.Status, calls, err)
				}
			} else {
				if err == nil || calls != 1 || state.Status != domain.JobSending {
					t.Fatal("failed settlement was not atomic", state.Status, calls, err)
				}
			}
			if _, err := pool.Exec(ctx, `ALTER TABLE notifications.push_jobs DROP CONSTRAINT fail_job_write`); err != nil {
				t.Fatal(err)
			}
			active = false
			clock.now = now.Add(domain.JobLeaseDuration + time.Second)
			if err := run.Handle(ctx); err != nil {
				t.Fatal(err)
			}
			state = jobState(t, store, job.Snapshot().ID)
			want := domain.JobAccepted
			if phase == "accepted" {
				want = domain.JobUncertain
			}
			if state.Status != want || calls != 1 {
				t.Fatal("unsafe rollback recovery", state.Status, calls)
			}
		})
	}
}

// A separate test executable is deliberately killed after its durable send marker.
func TestPushJobCrashHelper(t *testing.T) {
	if os.Getenv("PUSH_JOB_CRASH_HELPER") != "1" {
		return
	}
	pool, err := storage.Open(context.Background(), storage.Config{URL: os.Getenv("TEST_POSTGRES_URL")})
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	store := pg.New(pool)
	job, found, err := store.FindPushJob(context.Background(), id.ID(os.Getenv("PUSH_JOB_ID")))
	if err != nil || !found {
		t.Fatal(err)
	}
	now := job.Snapshot().CreatedAt
	run := command.RunPushJob{Store: store, IDs: id.Random{}, Send: command.SendPushPreview{Targets: store, Clock: &pushClock{now}, Sessions: eligibleFunc(func(context.Context, id.ID, id.ID) (bool, error) { return true, nil }), Gateway: gatewayFunc(func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error) {
		if err := os.WriteFile(os.Getenv("PUSH_JOB_PROBE"), []byte("one external attempt"), 0600); err != nil {
			t.Fatal(err)
		}
		fmt.Println("send-started")
		time.Sleep(time.Hour)
		return app.PushAccepted, nil
	})}}
	if err := run.Handle(context.Background()); err != nil {
		t.Fatal(err)
	}
}
func TestPushJobPostgresSIGKILLNeverReplaysUnknownSend(t *testing.T) {
	url := os.Getenv("TEST_POSTGRES_URL")
	if url == "" || os.Getenv("TEST_POSTGRES_DISPOSABLE") != "1" {
		t.Skip("disposable PostgreSQL only")
	}
	pool, err := storage.Open(context.Background(), storage.Config{URL: url})
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	store := pg.New(pool)
	now := time.Now().UTC().Truncate(time.Microsecond)
	job, _ := queuedJob(t, store, now)
	probe := filepath.Join(t.TempDir(), "external-attempt")
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	child := exec.CommandContext(ctx, os.Args[0], "-test.run=^TestPushJobCrashHelper$")
	child.Env = append(os.Environ(), "PUSH_JOB_CRASH_HELPER=1", "PUSH_JOB_ID="+job.Snapshot().ID.String(), "PUSH_JOB_PROBE="+probe)
	out, err := child.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	if err := child.Start(); err != nil {
		t.Fatal(err)
	}
	defer func() { _ = child.Process.Kill(); _ = child.Wait() }()
	scanner := bufio.NewScanner(out)
	ready := false
	for scanner.Scan() {
		if strings.Contains(scanner.Text(), "send-started") {
			ready = true
			break
		}
	}
	if !ready {
		t.Fatal("child did not reach external attempt", ctx.Err())
	}
	_ = child.Process.Kill()
	_ = child.Wait()
	// Reopen an independent adapter after process death, then advance only its injected clock.
	replacement, err := storage.Open(context.Background(), storage.Config{URL: url})
	if err != nil {
		t.Fatal(err)
	}
	defer replacement.Close()
	restored := pg.New(replacement)
	var calls int
	run := command.RunPushJob{Store: restored, IDs: id.Random{}, Send: command.SendPushPreview{Targets: restored, Clock: &pushClock{now.Add(domain.JobLeaseDuration + time.Second)}, Sessions: eligibleFunc(func(context.Context, id.ID, id.ID) (bool, error) { return true, nil }), Gateway: gatewayFunc(func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error) {
		calls++
		return app.PushAccepted, nil
	})}}
	if err := run.Handle(context.Background()); err != nil {
		t.Fatal(err)
	}
	if calls != 0 || jobState(t, restored, job.Snapshot().ID).Status != domain.JobUncertain {
		t.Fatal("crashed send replayed")
	}
	if data, err := os.ReadFile(probe); err != nil || string(data) != "one external attempt" {
		t.Fatal("external attempt evidence", err)
	}
}
