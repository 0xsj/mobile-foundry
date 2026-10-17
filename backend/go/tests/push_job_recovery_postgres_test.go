package tests

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	pg "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/postgres"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"os"
	"testing"
	"time"
)

func TestPushJobRecoveryPostgresAtomicityAndReopen(t *testing.T) {
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
	now := time.Now().UTC().Truncate(time.Microsecond)
	source, mutation := queuedJob(t, store, now)
	func() {
		if _, err := pool.Exec(ctx, `ALTER TABLE notifications.push_jobs ADD CONSTRAINT fail_recovery CHECK(status<>'canceled') NOT VALID`); err != nil {
			t.Fatal(err)
		}
		defer func() { _, _ = pool.Exec(ctx, `ALTER TABLE notifications.push_jobs DROP CONSTRAINT fail_recovery`) }()
		if _, err := store.CancelPushJob(ctx, jobOwner(source), now); err == nil {
			t.Fatal("cancel write failure hidden")
		}
		if jobState(t, store, source.Snapshot().ID) != source.Snapshot() {
			t.Fatal("failed cancellation changed job")
		}
	}()
	canceled, err := store.CancelPushJob(ctx, jobOwner(source), now)
	if err != nil {
		t.Fatal(err)
	}
	replace := command.ReplacePushJob{Store: store, Targets: store, Clock: &pushClock{now.Add(time.Second)}}
	child := randomID(t)
	func() {
		if _, err := pool.Exec(ctx, `ALTER TABLE notifications.push_jobs ADD CONSTRAINT fail_recovery CHECK(retry_of IS NULL) NOT VALID`); err != nil {
			t.Fatal(err)
		}
		defer func() { _, _ = pool.Exec(ctx, `ALTER TABLE notifications.push_jobs DROP CONSTRAINT fail_recovery`) }()
		if _, err := replace.Handle(ctx, app.PushJobLease(source), source.Snapshot().ID, child, false); err == nil {
			t.Fatal("replacement write failure hidden")
		}
		if _, found, err := store.FindPushJobReplacement(ctx, jobOwner(source)); err != nil || found {
			t.Fatal("failed insertion reserved replacement", err)
		}
		if jobState(t, store, source.Snapshot().ID) != canceled.Snapshot() {
			t.Fatal("failed insertion changed source")
		}
	}()
	replacement, err := replace.Handle(ctx, app.PushJobLease(source), source.Snapshot().ID, child, false)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := store.CancelPushJob(ctx, jobOwner(replacement), now.Add(2*time.Second)); err != nil {
		t.Fatal(err)
	}
	reopened, err := storage.Open(ctx, storage.Config{URL: url})
	if err != nil {
		t.Fatal(err)
	}
	defer reopened.Close()
	fresh := pg.New(reopened)
	got, found, err := fresh.FindPushJobReplacement(ctx, jobOwner(source))
	if err != nil || !found || got.Snapshot().RetryOf != source.Snapshot().ID || got.Snapshot().Status != domain.JobCanceled {
		t.Fatal("recovery state not durable", err)
	}
	replay, err := (command.ReplacePushJob{Store: fresh, Targets: fresh, Clock: &pushClock{now.Add(3 * time.Second)}}).Handle(ctx, app.PushJobLease(source), source.Snapshot().ID, child, false)
	if err != nil || replay.Snapshot() != got.Snapshot() {
		t.Fatal("reopened replay", err)
	}
	if _, err := (command.CancelPushJob{Store: fresh, Clock: &pushClock{now.Add(4 * time.Second)}}).Handle(ctx, mutation.Actor, mutation.ID, source.Snapshot().ID); err != nil {
		t.Fatal(err)
	}
}
