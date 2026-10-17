package integration

import (
	"context"
	"errors"
	"fmt"
	"os"
	"sync"
	"testing"
	"time"

	accountapp "github.com/0xsj/mobile-foundry/backend/internal/account/app"
	accountdomain "github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	accountstore "github.com/0xsj/mobile-foundry/backend/internal/account/infra/postgres"
	auditapp "github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	auditdomain "github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	auditstore "github.com/0xsj/mobile-foundry/backend/internal/audit/infra/postgres"
	identitydomain "github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	identitystore "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/security"
	"github.com/0xsj/mobile-foundry/backend/migrations"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type constantIDs struct{ value id.ID }

func (g constantIDs) NewID() (id.ID, error) { return g.value, nil }
func identity(t *testing.T) id.ID {
	t.Helper()
	value, err := (id.Random{}).NewID()
	if err != nil {
		t.Fatal(err)
	}
	return value
}
func execute(t *testing.T, pool *pgxpool.Pool, sql string, args ...any) {
	t.Helper()
	if _, err := pool.Exec(context.Background(), sql, args...); err != nil {
		t.Fatal(err)
	}
}
func count(t *testing.T, pool *pgxpool.Pool, sql string, args ...any) int {
	t.Helper()
	var n int
	if err := pool.QueryRow(context.Background(), sql, args...).Scan(&n); err != nil {
		t.Fatal(err)
	}
	return n
}
func freshAccount(t *testing.T) accountdomain.Account {
	t.Helper()
	a, err := accountdomain.New(identity(t), identity(t), "Original", time.Now())
	if err != nil {
		t.Fatal(err)
	}
	return a
}
func create(t *testing.T, store *accountstore.Store, a accountdomain.Account) {
	t.Helper()
	fact, _ := a.Fact(accountdomain.Created, a.Snapshot().OwnerID)
	if err := store.Create(context.Background(), a, fact, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
}

func TestPostgres(t *testing.T) {
	url := os.Getenv("TEST_POSTGRES_URL")
	if url == "" || os.Getenv("TEST_POSTGRES_DISPOSABLE") != "1" {
		t.Skip("make integration-test supplies an isolated disposable database")
	}
	ctx := context.Background()
	pool, err := storage.Open(ctx, storage.Config{URL: url})
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	var version string
	if err := pool.QueryRow(ctx, "SHOW server_version").Scan(&version); err != nil {
		t.Fatal(err)
	}
	t.Log("PostgreSQL", version)
	t.Run("concurrent migration bootstrap", func(t *testing.T) {
		tx, err := pool.Begin(ctx)
		if err != nil {
			t.Fatal(err)
		}
		defer tx.Rollback(ctx)
		if _, err := tx.Exec(ctx, "SELECT pg_advisory_xact_lock(hashtextextended($1,0))", "mobile-foundry/migrations/locked_probe"); err != nil {
			t.Fatal(err)
		}
		locked := storage.Schema{Name: "locked_probe", Migrations: []storage.Migration{{Name: "0001_probe.sql", SQL: "SELECT 1"}}}
		done := make(chan error, 1)
		go func() { done <- locked.Apply(ctx, pool) }()
		select {
		case err := <-done:
			t.Fatal("migrator bypassed schema lock", err)
		case <-time.After(100 * time.Millisecond):
		}
		if count(t, pool, "SELECT count(*) FROM pg_namespace WHERE nspname='locked_probe'") != 0 {
			t.Fatal("schema created before lock acquisition")
		}
		if err := tx.Rollback(ctx); err != nil {
			t.Fatal(err)
		}
		if err := <-done; err != nil {
			t.Fatal(err)
		}
		for i := range 24 {
			schema := storage.Schema{Name: fmt.Sprintf("migration_probe_%d", i), Migrations: []storage.Migration{{Name: "0001_probe.sql", SQL: "SELECT 1"}}}
			var group sync.WaitGroup
			failures := make(chan error, 4)
			for range 4 {
				group.Go(func() { failures <- schema.Apply(ctx, pool) })
			}
			group.Wait()
			close(failures)
			for err := range failures {
				if err != nil {
					t.Fatal("concurrent bootstrap", schema.Name, err)
				}
			}
			if count(t, pool, "SELECT count(*) FROM "+schema.Name+".schema_migrations") != 1 {
				t.Fatal("duplicate migration application")
			}
		}
	})
	for _, name := range []string{"account", "audit", "identity", "notes", "files"} {
		schema, err := storage.Load(migrations.Files, name)
		if err != nil {
			t.Fatal(err)
		}
		if err := schema.Check(ctx, pool); !errors.Is(err, storage.ErrSchema) {
			t.Fatal("fresh DB was not refused", err)
		}
		var legacy accountdomain.Account
		if name == "account" {
			// Exercise a retained 0001 database before applying the new mapping.
			prior := schema
			prior.Migrations = schema.Migrations[:1]
			if err := prior.Apply(ctx, pool); err != nil {
				t.Fatal(err)
			}
			legacy = freshAccount(t)
			store, err := accountstore.New(pool, id.Random{})
			if err != nil {
				t.Fatal(err)
			}
			create(t, store, legacy)
			if err := schema.Check(ctx, pool); !errors.Is(err, storage.ErrSchema) {
				t.Fatal("missing mapping migration accepted", err)
			}
		}
		var group sync.WaitGroup
		failures := make(chan error, 2)
		for range 2 {
			group.Go(func() { failures <- schema.Apply(ctx, pool) })
		}
		group.Wait()
		close(failures)
		for err := range failures {
			if err != nil {
				t.Fatal(err)
			}
		}
		if err := schema.Check(ctx, pool); err != nil {
			t.Fatal(err)
		}
		if name == "account" {
			store, err := accountstore.New(pool, id.Random{})
			if err != nil {
				t.Fatal(err)
			}
			current, found, err := store.Get(ctx, legacy.Snapshot().ID)
			if err != nil || !found || current.Snapshot() != legacy.Snapshot() || count(t, pool, "SELECT count(*) FROM account.personal_accounts") != 0 {
				t.Fatal("upgrade changed existing accounts or inferred personal mappings", err)
			}
			execute(t, pool, "UPDATE account.outbox SET delivered_at=clock_timestamp() WHERE account_id=$1", legacy.Snapshot().ID.String())
		}
		changed := schema
		changed.Migrations = append([]storage.Migration(nil), schema.Migrations...)
		changed.Migrations[0].SQL += "\n-- changed"
		if err := changed.Apply(ctx, pool); !errors.Is(err, storage.ErrSchema) {
			t.Fatal("applied checksum drift admitted", err)
		}
	}

	t.Run("state outbox rollback and concurrent CAS", func(t *testing.T) {
		a := freshAccount(t)
		store, err := accountstore.New(pool, id.Random{})
		if err != nil {
			t.Fatal(err)
		}
		create(t, store, a)
		var group sync.WaitGroup
		results := make(chan error, 2)
		for _, name := range []string{"One", "Two"} {
			group.Go(func() {
				next, _ := a.Rename(name, time.Now())
				fact, _ := next.Fact(accountdomain.Renamed, a.Snapshot().OwnerID)
				results <- store.Rename(ctx, next, 1, fact, events.Metadata{})
			})
		}
		group.Wait()
		close(results)
		success, conflict := 0, 0
		for err := range results {
			if err == nil {
				success++
			} else if errors.Is(err, accountapp.ErrConflict) {
				conflict++
			} else {
				t.Fatal(err)
			}
		}
		if success != 1 || conflict != 1 || count(t, pool, "SELECT count(*) FROM account.outbox WHERE account_id=$1", a.Snapshot().ID.String()) != 2 {
			t.Fatal("CAS and atomic outbox failed")
		}
		fixed := identity(t)
		collision, err := accountstore.New(pool, constantIDs{fixed})
		if err != nil {
			t.Fatal(err)
		}
		first, second := freshAccount(t), freshAccount(t)
		create(t, collision, first)
		fact, _ := second.Fact(accountdomain.Created, second.Snapshot().OwnerID)
		if err := collision.Create(ctx, second, fact, events.Metadata{}); err == nil {
			t.Fatal("duplicate event ID accepted")
		}
		if _, found, err := store.Get(ctx, second.Snapshot().ID); err != nil || found {
			t.Fatal("account committed without outbox", err)
		}
		execute(t, pool, "UPDATE account.outbox SET delivered_at=clock_timestamp()")
	})
	t.Run("personal mapping aggregate and outbox rollback together", func(t *testing.T) {
		store, err := accountstore.New(pool, constantIDs{identity(t)})
		if err != nil {
			t.Fatal(err)
		}
		seed := freshAccount(t)
		create(t, store, seed)
		candidate := freshAccount(t)
		fact, _ := candidate.Fact(accountdomain.Created, candidate.Snapshot().OwnerID)
		if _, _, err := store.Bootstrap(ctx, candidate, fact, events.Metadata{}); err == nil {
			t.Fatal("duplicate creation event accepted")
		}
		if count(t, pool, "SELECT count(*) FROM account.personal_accounts WHERE owner_id=$1", candidate.Snapshot().OwnerID.String()) != 0 || count(t, pool, "SELECT count(*) FROM account.accounts WHERE id=$1", candidate.Snapshot().ID.String()) != 0 {
			t.Fatal("event failure left an orphan mapping or account")
		}
		recovered, err := accountstore.New(pool, id.Random{})
		if err != nil {
			t.Fatal(err)
		}
		if _, created, err := recovered.Bootstrap(ctx, candidate, fact, events.Metadata{}); err != nil || !created {
			t.Fatal("mapping was poisoned by rollback", err)
		}
		// The module-owned FK also rejects a mapping to another owner's account.
		if err := storage.Transaction(ctx, pool, func(tx pgx.Tx) error {
			_, err := tx.Exec(ctx, "INSERT INTO account.personal_accounts(owner_id,account_id) VALUES($1,$2)", identity(t).String(), seed.Snapshot().ID.String())
			return err
		}); err == nil {
			t.Fatal("cross-owner mapping committed")
		}
		execute(t, pool, "UPDATE account.outbox SET delivered_at=clock_timestamp()")
	})
	t.Run("identity writes rollback with outbox and leases recover", func(t *testing.T) {
		eventID := identity(t)
		store, err := identitystore.New(pool, constantIDs{eventID})
		if err != nil {
			t.Fatal(err)
		}
		p, err := identitydomain.NewPrincipal(identity(t), "atomic-"+identity(t).String(), "Person", "opaque-hash", time.Now())
		if err != nil {
			t.Fatal(err)
		}
		if err := store.Register(ctx, p, events.Metadata{}); err != nil {
			t.Fatal(err)
		}
		second, _ := identitydomain.NewPrincipal(identity(t), "atomic-"+identity(t).String(), "Other", "opaque-hash", time.Now())
		if err := store.Register(ctx, second, events.Metadata{}); err == nil {
			t.Fatal("failed event append accepted")
		}
		if _, found, err := store.ByID(ctx, second.Snapshot().ID); err != nil || found {
			t.Fatal("principal committed without event", err)
		}
		_, ad, _ := (security.Tokens{}).Issue(ctx, "access")
		_, rd, _ := (security.Tokens{}).Issue(ctx, "refresh")
		session, _ := identitydomain.NewSession(identity(t), p.Snapshot().ID, ad, rd, time.Now())
		if err := store.Issue(ctx, session, events.Metadata{}); err == nil {
			t.Fatal("session issued without event")
		}
		if count(t, pool, "SELECT count(*) FROM identity.sessions WHERE id=$1", session.Snapshot().ID.String()) != 0 || count(t, pool, "SELECT count(*) FROM identity.refresh_credentials WHERE digest=$1", rd) != 0 {
			t.Fatal("session/refresh partially committed")
		}
		claimed, err := store.Pending(ctx, 1)
		if err != nil || len(claimed) != 1 {
			t.Fatal(err)
		}
		other, _ := identitystore.New(pool, id.Random{})
		if pending, err := other.Pending(ctx, 1); err != nil || len(pending) != 0 {
			t.Fatal("active Identity claim ignored", err)
		}
		execute(t, pool, "UPDATE identity.outbox SET lease_until=clock_timestamp()-interval '1 second' WHERE event_id=$1", eventID.String())
		recovered, err := other.Pending(ctx, 1)
		if err != nil || len(recovered) != 1 || recovered[0].ID != eventID {
			t.Fatal("Identity event identity lost", err)
		}
		if store.Delivered(ctx, eventID) == nil {
			t.Fatal("old Identity owner acknowledged new claim")
		}
		if err := other.Delivered(ctx, eventID); err != nil {
			t.Fatal(err)
		}
	})

	t.Run("lease recovery stale acknowledgement and per account order", func(t *testing.T) {
		first, _ := accountstore.New(pool, id.Random{})
		second, _ := accountstore.New(pool, id.Random{})
		a := freshAccount(t)
		create(t, first, a)
		next, _ := a.Rename("Next", time.Now())
		fact, _ := next.Fact(accountdomain.Renamed, a.Snapshot().OwnerID)
		if err := first.Rename(ctx, next, 1, fact, events.Metadata{}); err != nil {
			t.Fatal(err)
		}
		claimed, err := first.Pending(ctx, 32)
		if err != nil || len(claimed) != 1 || claimed[0].Version != 1 {
			t.Fatal(claimed, err)
		}
		if blocked, err := second.Pending(ctx, 32); err != nil || len(blocked) != 0 {
			t.Fatal("active lease or account ordering ignored", err)
		}
		execute(t, pool, "UPDATE account.outbox SET lease_until=clock_timestamp()-interval '1 second' WHERE event_id=$1", claimed[0].ID.String())
		recovered, err := second.Pending(ctx, 32)
		if err != nil || len(recovered) != 1 || recovered[0].ID != claimed[0].ID {
			t.Fatal("stable recovery failed", err)
		}
		if err := first.Delivered(ctx, claimed[0].ID); err == nil {
			t.Fatal("stale owner acknowledged another claim")
		}
		if err := second.Delivered(ctx, recovered[0].ID); err != nil {
			t.Fatal(err)
		}
		pending, err := first.Pending(ctx, 32)
		if err != nil || len(pending) != 1 || pending[0].Version != 2 {
			t.Fatal("next account event unavailable", err)
		}
		if err := first.Failed(ctx, pending[0].ID, "unavailable", 1); err != nil {
			t.Fatal(err)
		}
		restarted, _ := accountstore.New(pool, id.Random{})
		if restarted.Ready(ctx) == nil {
			t.Fatal("dead delivery forgotten on restart")
		}
		execute(t, pool, "UPDATE account.outbox SET delivered_at=clock_timestamp(),dead=false,lease_owner=NULL,lease_until=NULL")
	})

	t.Run("inbox atomicity replay and cursor isolation", func(t *testing.T) {
		store := auditstore.New(pool)
		e := auditdomain.Evidence{EventID: identity(t), AccountID: identity(t), ActorID: identity(t), Action: "account.created", Version: 1, OccurredAt: time.Now().UTC().Truncate(time.Microsecond), RequestID: "request.1", CorrelationID: "correlation.1"}
		entry, err := auditdomain.New(e, time.Now())
		if err != nil {
			t.Fatal(err)
		}
		var group sync.WaitGroup
		failures := make(chan error, 8)
		for range 8 {
			group.Go(func() { failures <- store.Record(ctx, "audit.account", entry) })
		}
		group.Wait()
		close(failures)
		for err := range failures {
			if err != nil {
				t.Fatal(err)
			}
		}
		page, err := store.List(ctx, e.AccountID, 0, 1)
		if err != nil || len(page.Records) != 1 || page.Next != 0 {
			t.Fatal(page, err)
		}
		original := page.Records[0].Entry.RecordedAt()
		replay, _ := auditdomain.New(e, time.Now().Add(time.Second))
		if err := store.Record(ctx, "audit.account", replay); err != nil {
			t.Fatal(err)
		}
		changed := e
		changed.CorrelationID = "changed"
		different, _ := auditdomain.New(changed, time.Now())
		if err := store.Record(ctx, "audit.account", different); !errors.Is(err, auditapp.ErrReplay) {
			t.Fatal("conflicting replay admitted", err)
		}
		e.EventID = identity(t)
		e.Action = "account.renamed"
		e.Version = 2
		next, _ := auditdomain.New(e, time.Now())
		if err := store.Record(ctx, "audit.account", next); err != nil {
			t.Fatal(err)
		}
		page, err = store.List(ctx, e.AccountID, 0, 1)
		if err != nil || page.Next == 0 || page.Records[0].Entry.RecordedAt() != original {
			t.Fatal("pagination/replay changed evidence", err)
		}
		last, err := store.List(ctx, e.AccountID, page.Next, 1)
		if err != nil || len(last.Records) != 1 || last.Next != 0 || last.Records[0].Entry.Evidence().Version != 2 {
			t.Fatal(last, err)
		}
		other, err := store.List(ctx, identity(t), 0, 1)
		if err != nil || len(other.Records) != 0 {
			t.Fatal("cross account evidence", err)
		}
		execute(t, pool, "ALTER TABLE audit.inbox ADD CONSTRAINT reject_test_consumer CHECK (consumer <> 'reject-test')")
		defer execute(t, pool, "ALTER TABLE audit.inbox DROP CONSTRAINT reject_test_consumer")
		e.EventID = identity(t)
		failed, _ := auditdomain.New(e, time.Now())
		before := count(t, pool, "SELECT count(*) FROM audit.entries")
		if err := store.Record(ctx, "reject-test", failed); err == nil {
			t.Fatal("inbox failure ignored")
		}
		if count(t, pool, "SELECT count(*) FROM audit.entries") != before {
			t.Fatal("evidence committed without inbox")
		}
	})

	t.Run("lost acknowledgement replays committed evidence", func(t *testing.T) {
		first, _ := accountstore.New(pool, id.Random{})
		second, _ := accountstore.New(pool, id.Random{})
		a := freshAccount(t)
		create(t, first, a)
		pending, err := first.Pending(ctx, 1)
		if err != nil || len(pending) != 1 {
			t.Fatal(err)
		}
		message := pending[0]
		e := auditdomain.Evidence{EventID: message.ID, AccountID: message.AccountID, ActorID: message.ActorID, Action: "account.created", Version: message.Version, OccurredAt: message.OccurredAt, RequestID: message.Metadata.RequestID, CorrelationID: message.Metadata.CorrelationID}
		entry, _ := auditdomain.New(e, time.Now())
		store := auditstore.New(pool)
		if err := store.Record(ctx, "audit.account", entry); err != nil {
			t.Fatal(err)
		}
		// The producer never sees an acknowledgment. A new owner later retries.
		execute(t, pool, "UPDATE account.outbox SET lease_until=clock_timestamp()-interval '1 second' WHERE event_id=$1", message.ID.String())
		replay, err := second.Pending(ctx, 1)
		if err != nil || len(replay) != 1 || replay[0].ID != message.ID {
			t.Fatal(err)
		}
		recordedAgain, _ := auditdomain.New(e, time.Now().Add(time.Second))
		if err := auditstore.New(pool).Record(ctx, "audit.account", recordedAgain); err != nil {
			t.Fatal(err)
		}
		if err := second.Delivered(ctx, message.ID); err != nil {
			t.Fatal(err)
		}
		page, err := store.List(ctx, message.AccountID, 0, 10)
		if err != nil || len(page.Records) != 1 || page.Records[0].Entry.RecordedAt() != entry.RecordedAt() {
			t.Fatal("replay duplicated or changed durable evidence", err)
		}
	})

	t.Run("cancelled transaction and serialized append", func(t *testing.T) {
		cancelled, cancel := context.WithCancel(ctx)
		cancel()
		if err := storage.Transaction(cancelled, pool, func(pgx.Tx) error { return nil }); !errors.Is(err, context.Canceled) {
			t.Fatal(err)
		}
		cancelDuring, cancelWrite := context.WithCancel(ctx)
		before := count(t, pool, "SELECT next_sequence FROM audit.append_state WHERE singleton")
		err := storage.Transaction(cancelDuring, pool, func(tx pgx.Tx) error {
			if _, err := tx.Exec(cancelDuring, "UPDATE audit.append_state SET next_sequence=next_sequence+1 WHERE singleton"); err != nil {
				return err
			}
			cancelWrite()
			return nil
		})
		if !errors.Is(err, context.Canceled) || count(t, pool, "SELECT next_sequence FROM audit.append_state WHERE singleton") != before {
			t.Fatal("cancellation before commit changed state", err)
		}
		tx, err := pool.Begin(ctx)
		if err != nil {
			t.Fatal(err)
		}
		var sequence int64
		if err := tx.QueryRow(ctx, "SELECT next_sequence FROM audit.append_state WHERE singleton FOR UPDATE").Scan(&sequence); err != nil {
			t.Fatal(err)
		}
		store := auditstore.New(pool)
		e := auditdomain.Evidence{EventID: identity(t), AccountID: identity(t), ActorID: identity(t), Action: "account.created", Version: 1, OccurredAt: time.Now().UTC().Truncate(time.Microsecond)}
		entry, _ := auditdomain.New(e, time.Now())
		done := make(chan error, 1)
		go func() { done <- store.Record(ctx, "audit.account", entry) }()
		select {
		case err := <-done:
			t.Fatal("append did not wait for earlier sequence owner", err)
		case <-time.After(100 * time.Millisecond):
		}
		if err := tx.Rollback(ctx); err != nil {
			t.Fatal(err)
		}
		if err := <-done; err != nil {
			t.Fatal(err)
		}
		page, err := store.List(ctx, e.AccountID, 0, 1)
		if err != nil || len(page.Records) != 1 || page.Records[0].Sequence != uint64(sequence) {
			t.Fatal("rollback left sequence hole", err)
		}
	})

	t.Run("dependency outage is not absence and corrupt outbox is quarantined", func(t *testing.T) {
		store, _ := accountstore.New(pool, id.Random{})
		a := freshAccount(t)
		create(t, store, a)
		tx, err := pool.Begin(ctx)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := tx.Exec(ctx, "LOCK TABLE account.accounts IN ACCESS EXCLUSIVE MODE"); err != nil {
			t.Fatal(err)
		}
		bounded, cancel := context.WithTimeout(ctx, 30*time.Millisecond)
		_, found, err := store.Get(bounded, a.Snapshot().ID)
		cancel()
		if err == nil || found {
			t.Fatal("dependency failure became absence", err)
		}
		if err := tx.Rollback(ctx); err != nil {
			t.Fatal(err)
		}
		execute(t, pool, "UPDATE account.outbox SET envelope='{}' WHERE account_id=$1", a.Snapshot().ID.String())
		if _, err := store.Pending(ctx, 1); err == nil {
			t.Fatal("corrupt envelope delivered")
		}
		if store.Ready(ctx) == nil {
			t.Fatal("corrupt envelope remained ready")
		}
		execute(t, pool, "UPDATE account.outbox SET delivered_at=clock_timestamp(),dead=false,lease_owner=NULL,lease_until=NULL")
	})
}
