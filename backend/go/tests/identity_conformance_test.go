package tests

import (
	"context"
	"errors"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/memory"
	identitypg "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/security"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
)

func randomID(t *testing.T) id.ID {
	t.Helper()
	value, err := (id.Random{}).NewID()
	if err != nil {
		t.Fatal(err)
	}
	return value
}
func principalValue(t *testing.T, now time.Time) domain.Principal {
	t.Helper()
	value, err := domain.NewPrincipal(randomID(t), "user-"+randomID(t).String(), "Person", "opaque-test-hash", now)
	if err != nil {
		t.Fatal(err)
	}
	return value
}
func tokenPair(t *testing.T) (string, string, string, string) {
	t.Helper()
	a, ad, e1 := (security.Tokens{}).Issue(context.Background(), "access")
	r, rd, e2 := (security.Tokens{}).Issue(context.Background(), "refresh")
	if e1 != nil || e2 != nil {
		t.Fatal(e1, e2)
	}
	return a, ad, r, rd
}
func storeConformance(t *testing.T, store app.Store) {
	ctx := context.Background()
	now := time.Date(2026, 10, 13, 0, 0, 0, 0, time.UTC)
	p := principalValue(t, now)
	if err := store.Register(ctx, p, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	duplicate, err := domain.NewPrincipal(randomID(t), p.Snapshot().Login, "Duplicate", "opaque-test-hash", now)
	if err != nil {
		t.Fatal(err)
	}
	if err := store.Register(ctx, duplicate, events.Metadata{}); !errors.Is(err, app.ErrExists) {
		t.Fatal("duplicate login admitted", err)
	}
	stored, found, err := store.ByLogin(ctx, p.Snapshot().Login)
	if err != nil || !found || stored.Snapshot() != p.Snapshot() {
		t.Fatal("principal restore failed", err)
	}
	_, ad, _, rd := tokenPair(t)
	session, err := domain.NewSession(randomID(t), p.Snapshot().ID, ad, rd, now)
	if err != nil {
		t.Fatal(err)
	}
	if err := store.Issue(ctx, session, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	if actor, err := store.Access(ctx, ad, now); err != nil || actor != p.Snapshot().ID {
		t.Fatal("credential did not resolve", err)
	}
	if _, err := store.Access(ctx, ad, now.Add(domain.AccessLifetime)); !errors.Is(err, domain.ErrCredential) {
		t.Fatal("expired grant accepted", err)
	}
	var group sync.WaitGroup
	type result struct {
		s     domain.Session
		reuse bool
		err   error
	}
	results := make(chan result, 2)
	for range 2 {
		group.Go(func() {
			_, nextAD, _, nextRD := tokenPair(t)
			s, reused, err := store.Rotate(ctx, rd, nextAD, nextRD, now.Add(time.Second), events.Metadata{})
			results <- result{s, reused, err}
		})
	}
	group.Wait()
	close(results)
	accepted, reused := 0, 0
	var renewed domain.Session
	for result := range results {
		if result.err != nil {
			t.Fatal(result.err)
		}
		if result.reuse {
			reused++
		} else {
			accepted++
			renewed = result.s
		}
	}
	if accepted != 1 || reused != 1 {
		t.Fatal("refresh compare-and-swap failed", accepted, reused)
	}
	if _, err := store.Access(ctx, renewed.Snapshot().AccessDigest, now.Add(time.Second)); !errors.Is(err, domain.ErrCredential) {
		t.Fatal("reuse did not revoke current access", err)
	}
	_, secondAD, _, secondRD := tokenPair(t)
	second, _ := domain.NewSession(randomID(t), p.Snapshot().ID, secondAD, secondRD, now)
	if err := store.Issue(ctx, second, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	if err := store.Revoke(ctx, secondRD, now.Add(20*time.Minute), events.Metadata{}); err != nil {
		t.Fatal("revocation after access expiry failed", err)
	}
	if err := store.Revoke(ctx, secondRD, now.Add(time.Hour), events.Metadata{}); err != nil {
		t.Fatal("repeated sign out was not idempotent", err)
	}
	if _, err := store.Access(ctx, secondAD, now); !errors.Is(err, domain.ErrCredential) {
		t.Fatal("revoked credential resolved", err)
	}
	_, thirdAD, _, thirdRD := tokenPair(t)
	third, _ := domain.NewSession(randomID(t), p.Snapshot().ID, thirdAD, thirdRD, now)
	if err := store.Issue(ctx, third, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	_, nextAD, _, nextRD := tokenPair(t)
	next, reuse, err := store.Rotate(ctx, thirdRD, nextAD, nextRD, now.Add(29*24*time.Hour+23*time.Hour+55*time.Minute), events.Metadata{})
	if err != nil || reuse || next.Snapshot().ExpiresAt != third.Snapshot().ExpiresAt || next.Snapshot().AccessExpiresAt != third.Snapshot().ExpiresAt {
		t.Fatal("renewal extended absolute expiry", err)
	}
	if _, _, err := store.Rotate(ctx, nextRD, ad, rd, now.Add(domain.SessionLifetime), events.Metadata{}); !errors.Is(err, domain.ErrCredential) {
		t.Fatal("expired refresh accepted", err)
	}
	cancelled, cancel := context.WithCancel(ctx)
	cancel()
	candidate := principalValue(t, now)
	if err := store.Register(cancelled, candidate, events.Metadata{}); !errors.Is(err, context.Canceled) {
		t.Fatal("cancelled registration admitted", err)
	}
	if _, found, err := store.ByID(ctx, candidate.Snapshot().ID); found || err != nil {
		t.Fatal("cancelled registration committed", err)
	}
}
func TestIdentityStoreConformance(t *testing.T) {
	t.Run("memory", func(t *testing.T) { storeConformance(t, memory.New(id.Random{})) })
	url := os.Getenv("TEST_POSTGRES_URL")
	if url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		t.Run("postgres", func(t *testing.T) {
			pool, err := storage.Open(context.Background(), storage.Config{URL: url})
			if err != nil {
				t.Fatal(err)
			}
			defer pool.Close()
			store, err := identitypg.New(pool, id.Random{})
			if err != nil {
				t.Fatal(err)
			}
			storeConformance(t, store)
		})
	}
}
