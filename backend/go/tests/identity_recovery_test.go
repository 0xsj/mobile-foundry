package tests

import (
	"context"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/memory"
	pg "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/security"
	"github.com/0xsj/mobile-foundry/backend/migrations"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

type recoveryStore interface {
	app.Store
	app.RecoveryStore
	Pending(context.Context, int) ([]events.Envelope, error)
}

func recoveryContract(t *testing.T, s recoveryStore) {
	t.Helper()
	ctx := context.Background()
	now := domain.Time(time.Now())
	actor := freshID(t)
	p, err := domain.NewPrincipal(actor, "recovery-"+actor.String(), "Recovery", "before-private-hash", now)
	if err != nil {
		t.Fatal(err)
	}
	if err = s.Register(ctx, p, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	address := actor.String() + "@example.test"
	tokens := security.Tokens{}
	challenge := func(purpose, hash string, at time.Time) (string, domain.Challenge) {
		token, digest, err := tokens.Issue(ctx, purpose)
		if err != nil {
			t.Fatal(err)
		}
		return token, domain.Challenge{PrincipalID: actor, Purpose: purpose, Email: address, Digest: digest, CredentialHash: hash, CreatedAt: at, ExpiresAt: at.Add(20 * time.Minute)}
	}
	issue := func(hash string) domain.Session {
		_, a, _ := tokens.Issue(ctx, "access")
		_, r, _ := tokens.Issue(ctx, "refresh")
		session, err := domain.NewSession(freshID(t), actor, a, r, now)
		if err != nil {
			t.Fatal(err)
		}
		if err = s.IssueChecked(ctx, session, hash, events.Metadata{}); err != nil {
			t.Fatal(err)
		}
		return session
	}
	first, second := issue("before-private-hash"), issue("before-private-hash")
	raw, verify := challenge(domain.VerifyEmail, "before-private-hash", now)
	if err = s.BeginChallenge(ctx, verify); err != nil {
		t.Fatal(err)
	}
	if err = s.ResetPassword(ctx, verify.Digest, "wrong-purpose-hash", now, events.Metadata{}); err == nil {
		t.Fatal("verification token used to reset")
	}
	if err = s.VerifyEmail(ctx, verify.Digest, now, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	if err = s.VerifyEmail(ctx, verify.Digest, now, events.Metadata{}); err == nil {
		t.Fatal("token reused")
	}
	contact, ok, err := s.Contact(ctx, actor)
	if err != nil || !ok || contact.Email != address {
		t.Fatal("verified contact missing", err)
	}
	if _, ok, err = s.ByEmail(ctx, address); err != nil || !ok {
		t.Fatal("verified address unavailable", err)
	}
	_, old := challenge(domain.ResetPassword, "before-private-hash", now)
	if err = s.BeginChallenge(ctx, old); err != nil {
		t.Fatal(err)
	}
	_, current := challenge(domain.ResetPassword, "before-private-hash", now)
	if err = s.BeginChallenge(ctx, current); err != nil {
		t.Fatal(err)
	}
	if err = s.ResetPassword(ctx, old.Digest, "ignored-private-hash", now, events.Metadata{}); err == nil {
		t.Fatal("replaced challenge accepted")
	}
	if err = s.ResetPassword(ctx, current.Digest, "ignored-private-hash", current.ExpiresAt, events.Metadata{}); err == nil {
		t.Fatal("expired challenge accepted")
	}
	var winners atomic.Int32
	var group sync.WaitGroup
	for range 8 {
		group.Add(1)
		go func() {
			defer group.Done()
			if s.ResetPassword(ctx, current.Digest, "after-private-hash", now, events.Metadata{}) == nil {
				winners.Add(1)
			}
		}()
	}
	group.Wait()
	if winners.Load() != 1 {
		t.Fatal("reset not single use", winners.Load())
	}
	for _, session := range []domain.Session{first, second} {
		if _, err = s.Access(ctx, session.Snapshot().AccessDigest, now); err == nil {
			t.Fatal("old access survived reset")
		}
		if _, _, err = s.Rotate(ctx, session.Snapshot().RefreshDigest, session.Snapshot().RefreshDigest, session.Snapshot().AccessDigest, now, events.Metadata{}); err == nil {
			t.Fatal("old refresh survived reset")
		}
	}
	_, a, _ := tokens.Issue(ctx, "access")
	_, r, _ := tokens.Issue(ctx, "refresh")
	stale, _ := domain.NewSession(freshID(t), actor, a, r, now)
	if err = s.IssueChecked(ctx, stale, "before-private-hash", events.Metadata{}); err == nil {
		t.Fatal("stale sign-in issued a session")
	}
	third := issue("after-private-hash")
	_, pending := challenge(domain.ResetPassword, "after-private-hash", now)
	if err = s.BeginChallenge(ctx, pending); err != nil {
		t.Fatal(err)
	}
	if err = s.ChangePassword(ctx, actor, "before-private-hash", "next-private-hash", now, events.Metadata{}); err == nil {
		t.Fatal("stale password checkpoint changed credentials")
	}
	if err = s.ChangePassword(ctx, actor, "after-private-hash", "next-private-hash", now, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	if _, err = s.Access(ctx, third.Snapshot().AccessDigest, now); err == nil {
		t.Fatal("password change retained session")
	}
	if err = s.ResetPassword(ctx, pending.Digest, "ignored-private-hash", now, events.Metadata{}); err == nil {
		t.Fatal("password change retained reset code")
	}
	_, expired := challenge(domain.ResetPassword, "next-private-hash", now)
	if err = s.BeginChallenge(ctx, expired); err != nil {
		t.Fatal(err)
	}
	if n, err := s.PruneChallenges(ctx, expired.ExpiresAt, 1); err != nil || n != 1 {
		t.Fatal("expired challenge pruning", n, err)
	}
	if _, err = s.PruneChallenges(ctx, now, 0); err == nil {
		t.Fatal("invalid prune bound")
	}
	messages, err := s.Pending(ctx, 1000)
	if err != nil {
		t.Fatal(err)
	}
	wire, _ := json.Marshal(messages)
	for _, secret := range []string{raw, address, "before-private-hash", "after-private-hash", "next-private-hash"} {
		if strings.Contains(string(wire), secret) {
			t.Fatal("private recovery data entered events")
		}
	}
}
func TestIdentityRecoveryMemory(t *testing.T) { recoveryContract(t, memory.New(id.Random{})) }
func TestIdentityRecoveryPostgres(t *testing.T) {
	url := os.Getenv("TEST_POSTGRES_URL")
	if url == "" || os.Getenv("TEST_POSTGRES_DISPOSABLE") != "1" {
		t.Skip("owned disposable database required")
	}
	ctx := context.Background()
	pool, err := storage.Open(ctx, storage.Config{URL: url})
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	schema, err := storage.Load(migrations.Files, "identity")
	if err != nil {
		t.Fatal(err)
	}
	if err = schema.Apply(ctx, pool); err != nil {
		t.Fatal(err)
	}
	store, err := pg.New(pool, id.Random{})
	if err != nil {
		t.Fatal(err)
	}
	recoveryContract(t, store)
}
func TestRecoveryEmailAdmission(t *testing.T) {
	for _, value := range []string{"a@", "a\r\n@example.test", "x <x@example.test>", "ü@example.test", "a..b@example.test"} {
		if _, err := domain.Email(value); err == nil {
			t.Fatal("invalid mailbox", value)
		}
	}
	if got, err := domain.Email(" Alex+work@Example.test "); err != nil || got != "alex+work@example.test" {
		t.Fatal(got, err)
	}
}

func freshID(t *testing.T) id.ID {
	t.Helper()
	v, err := (id.Random{}).NewID()
	if err != nil {
		t.Fatal(err)
	}
	return v
}

type recoveryFailIDs struct{ fail atomic.Bool }

func (g *recoveryFailIDs) NewID() (id.ID, error) {
	if g.fail.Load() {
		return "", events.ErrInvalid
	}
	return (id.Random{}).NewID()
}
func recoveryRollback(t *testing.T, makeStore func(id.Generator) recoveryStore) {
	t.Helper()
	ctx := context.Background()
	generator := &recoveryFailIDs{}
	s := makeStore(generator)
	now := domain.Time(time.Now())
	actor := freshID(t)
	p, _ := domain.NewPrincipal(actor, "rollback-"+actor.String(), "Rollback", "rollback-before-hash", now)
	if err := s.Register(ctx, p, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	tokens := security.Tokens{}
	_, a, _ := tokens.Issue(ctx, "access")
	_, r, _ := tokens.Issue(ctx, "refresh")
	session, _ := domain.NewSession(freshID(t), actor, a, r, now)
	if err := s.IssueChecked(ctx, session, "rollback-before-hash", events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	_, verify, _ := tokens.Issue(ctx, domain.VerifyEmail)
	c := domain.Challenge{PrincipalID: actor, Purpose: domain.VerifyEmail, Email: actor.String() + "@rollback.test", Digest: verify, CredentialHash: "rollback-before-hash", CreatedAt: now, ExpiresAt: now.Add(20 * time.Minute)}
	if err := s.BeginChallenge(ctx, c); err != nil {
		t.Fatal(err)
	}
	generator.fail.Store(true)
	if err := s.VerifyEmail(ctx, verify, now, events.Metadata{}); err == nil {
		t.Fatal("verification accepted without required fact")
	}
	if _, ok, err := s.Contact(ctx, actor); err != nil || ok {
		t.Fatal("partial contact commit", err)
	}
	generator.fail.Store(false)
	if err := s.VerifyEmail(ctx, verify, now, events.Metadata{}); err != nil {
		t.Fatal("challenge consumed on rollback", err)
	}
	_, reset, _ := tokens.Issue(ctx, domain.ResetPassword)
	c.Purpose = domain.ResetPassword
	c.Digest = reset
	if err := s.BeginChallenge(ctx, c); err != nil {
		t.Fatal(err)
	}
	generator.fail.Store(true)
	if err := s.ResetPassword(ctx, reset, "rollback-after-hash", now, events.Metadata{}); err == nil {
		t.Fatal("reset accepted without required fact")
	}
	current, ok, err := s.ByID(ctx, actor)
	if err != nil || !ok || current.Snapshot().PasswordHash != "rollback-before-hash" {
		t.Fatal("partial password commit", err)
	}
	if _, err = s.Access(ctx, a, now); err != nil {
		t.Fatal("partial session revocation", err)
	}
	generator.fail.Store(false)
	if err = s.ResetPassword(ctx, reset, "rollback-after-hash", now, events.Metadata{}); err != nil {
		t.Fatal("reset token consumed on rollback", err)
	}
}
func TestIdentityRecoveryAtomicMemory(t *testing.T) {
	recoveryRollback(t, func(ids id.Generator) recoveryStore { return memory.New(ids) })
}
func TestIdentityRecoveryAtomicPostgres(t *testing.T) {
	url := os.Getenv("TEST_POSTGRES_URL")
	if url == "" || os.Getenv("TEST_POSTGRES_DISPOSABLE") != "1" {
		t.Skip("owned disposable database required")
	}
	pool, err := storage.Open(context.Background(), storage.Config{URL: url})
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	schema, _ := storage.Load(migrations.Files, "identity")
	if err = schema.Apply(context.Background(), pool); err != nil {
		t.Fatal(err)
	}
	recoveryRollback(t, func(ids id.Generator) recoveryStore {
		s, err := pg.New(pool, ids)
		if err != nil {
			t.Fatal(err)
		}
		return s
	})
}
