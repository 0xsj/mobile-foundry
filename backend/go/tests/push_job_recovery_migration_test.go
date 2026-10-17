package tests

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	pg "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/migrations"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"io/fs"
	"net/url"
	"os"
	"strings"
	"testing"
	"testing/fstest"
	"time"
)

func TestPushJobRecoveryMigrationPreservesExistingReceipts(t *testing.T) {
	connection := os.Getenv("TEST_POSTGRES_URL")
	if connection == "" || os.Getenv("TEST_POSTGRES_DISPOSABLE") != "1" {
		t.Skip("disposable PostgreSQL only")
	}
	ctx := context.Background()
	admin, err := storage.Open(ctx, storage.Config{URL: connection})
	if err != nil {
		t.Fatal(err)
	}
	defer admin.Close()
	database := "job_upgrade_" + strings.ReplaceAll(randomID(t).String(), "-", "")
	identifier := pgx.Identifier{database}.Sanitize()
	if _, err := admin.Exec(ctx, `CREATE DATABASE `+identifier); err != nil {
		t.Fatal(err)
	}
	defer func() {
		if _, err := admin.Exec(ctx, `DROP DATABASE `+identifier); err != nil {
			t.Error(err)
		}
	}()
	parsed, err := url.Parse(connection)
	if err != nil {
		t.Fatal(err)
	}
	parsed.Path = "/" + database
	pool, err := storage.Open(ctx, storage.Config{URL: parsed.String()})
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	previous := fstest.MapFS{}
	for _, name := range []string{"0001_inbox_preferences.sql", "0002_installations.sql", "0003_push_jobs.sql"} {
		path := "notifications/" + name
		wire, err := fs.ReadFile(migrations.Files, path)
		if err != nil {
			t.Fatal(err)
		}
		previous[path] = &fstest.MapFile{Data: wire}
	}
	old, err := storage.Load(previous, "notifications")
	if err != nil {
		t.Fatal(err)
	}
	if err := old.Apply(ctx, pool); err != nil {
		t.Fatal(err)
	}
	now := time.Now().UTC().Truncate(time.Microsecond)
	states := map[string]domain.PushJobStatus{}
	for _, status := range []domain.PushJobStatus{domain.JobQueued, domain.JobAccepted, domain.JobUncertain} {
		identity := randomID(t).String()
		attempts, code := 0, ""
		if status != domain.JobQueued {
			attempts = 1
		}
		if status == domain.JobUncertain {
			code = "provider_unconfirmed"
		}
		_, err := pool.Exec(ctx, `INSERT INTO notifications.push_jobs(id,installation_id,principal_id,session_id,installation_version,status,attempts,created_at,updated_at,expires_at,next_at,code,retired) VALUES($1,$2,$3,$4,1,$5,$6,$7,$7,$8,$7,$9,false)`, identity, randomID(t).String(), randomID(t).String(), randomID(t).String(), status, attempts, now, now.Add(domain.JobLifetime), code)
		if err != nil {
			t.Fatal(err)
		}
		states[identity] = status
	}
	current, err := storage.Load(migrations.Files, "notifications")
	if err != nil {
		t.Fatal(err)
	}
	if err := current.Apply(ctx, pool); err != nil {
		t.Fatal(err)
	}
	if err := current.Check(ctx, pool); err != nil {
		t.Fatal(err)
	}
	for identity, status := range states {
		got, found, err := pg.New(pool).FindPushJob(ctx, id.ID(identity))
		if err != nil || !found {
			t.Fatal(err)
		}
		s := got.Snapshot()
		if s.Status != status || s.RetryOf != "" || s.DuplicateRiskAccepted || !s.CreatedAt.Equal(now) {
			t.Fatal("migration changed retained intent/outcome")
		}
	}
	if _, err := pool.Exec(ctx, `UPDATE notifications.push_jobs SET duplicate_risk_accepted=true`); err == nil {
		t.Fatal("root job admitted duplicate-risk evidence without lineage")
	}
	if _, err := pool.Exec(ctx, `UPDATE notifications.push_jobs SET retry_of=id`); err == nil {
		t.Fatal("self replacement admitted")
	}
}
