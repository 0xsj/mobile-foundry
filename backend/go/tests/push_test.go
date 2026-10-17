package tests

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	identityapp "github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	identityquery "github.com/0xsj/mobile-foundry/backend/internal/identity/app/query"
	identitydomain "github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	identitymemory "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/memory"
	identitypg "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/postgres"
	identitycap "github.com/0xsj/mobile-foundry/backend/internal/identity/transport/capability/v1"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/capabilities"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/memory"
	pg "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
)

type pushClock struct{ now time.Time }

func (c *pushClock) Now() time.Time { return c.now }

type eligibleFunc func(context.Context, id.ID, id.ID) (bool, error)

func (f eligibleFunc) SessionEligible(ctx context.Context, p, s id.ID) (bool, error) {
	return f(ctx, p, s)
}

type gatewayFunc func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error)

func (f gatewayFunc) SendPreview(ctx context.Context, a app.PushAddress, preview id.ID) (app.PushDisposition, error) {
	return f(ctx, a, preview)
}

type pushStore interface {
	app.Installations
	app.PushTargets
}
type sessionStatusStore interface {
	identityapp.Store
	identityquery.SessionStatus
}

func issuedPushSession(t *testing.T, s sessionStatusStore, now time.Time) (identitydomain.Session, string) {
	t.Helper()
	ctx := context.Background()
	p := principalValue(t, now)
	if err := s.Register(ctx, p, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	_, ad, _, rd := tokenPair(t)
	v, err := identitydomain.NewSession(randomID(t), p.Snapshot().ID, ad, rd, now)
	if err != nil {
		t.Fatal(err)
	}
	if err := s.Issue(ctx, v, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	return v, rd
}

func sessionStatusConformance(t *testing.T, s sessionStatusStore) {
	ctx := context.Background()
	now := time.Now().UTC().Truncate(time.Microsecond)
	v, refresh := issuedPushSession(t, s, now)
	p, session := v.Snapshot().PrincipalID, v.Snapshot().ID
	for _, row := range []struct {
		name               string
		principal, session id.ID
		at                 time.Time
		want               bool
	}{
		{"current", p, session, now, true},
		{"access_expired_but_session_live", p, session, now.Add(16 * time.Minute), true},
		{"before_creation", p, session, now.Add(-time.Second), false},
		{"absolute_expiry", p, session, v.Snapshot().ExpiresAt, false},
		{"wrong_principal", randomID(t), session, now, false},
		{"missing_session", p, randomID(t), now, false},
	} {
		t.Run(row.name, func(t *testing.T) {
			got, err := s.SessionEligible(ctx, row.principal, row.session, row.at)
			if err != nil || got != row.want {
				t.Fatal("session eligibility", got, err)
			}
		})
	}
	_, ad, _, rd := tokenPair(t)
	if _, reused, err := s.Rotate(ctx, refresh, ad, rd, now.Add(time.Minute), events.Metadata{}); err != nil || reused {
		t.Fatal("renew", err)
	}
	if ok, err := s.SessionEligible(ctx, p, session, now.Add(20*time.Minute)); err != nil || !ok {
		t.Fatal("renewed binding", err)
	}
	if err := s.Revoke(ctx, rd, now.Add(21*time.Minute), events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	if ok, err := s.SessionEligible(ctx, p, session, now.Add(22*time.Minute)); err != nil || ok {
		t.Fatal("revoked eligibility", err)
	}
	canceled, cancel := context.WithCancel(ctx)
	cancel()
	if _, err := s.SessionEligible(canceled, p, session, now); !errors.Is(err, context.Canceled) {
		t.Fatal("canceled lookup", err)
	}
}

func pushConformance(t *testing.T, store pushStore) {
	ctx := context.Background()
	for _, scenario := range []string{"accepted", "invalid", "uncertain", "unknown", "session_denied", "session_outage", "rotation_during_check", "expiry_during_check", "rotation_during_send", "rebind_during_send", "unregister_during_send", "revocation_during_check", "canceled", "missing", "stale", "wrong_owner", "disabled", "expired"} {
		t.Run(scenario, func(t *testing.T) {
			now := time.Now().UTC().Truncate(time.Microsecond)
			clock := &pushClock{now}
			a := installationActor(t, now)
			m := installationMutation(t, a)
			if _, err := store.RegisterInstallation(ctx, m, now); err != nil {
				t.Fatal(err)
			}
			lease := app.PushLease{InstallationID: m.ID, PrincipalID: a.Principal, SessionID: a.Session, Version: 1}
			preview := randomID(t)
			calls := 0
			dependency := errors.New("private dependency failure")
			rotate := func(rebind bool) {
				next := m
				next.Expected = 1
				next.RegistrationID = randomID(t)
				next.Spec.Token = "rotated-" + randomID(t).String()
				if rebind {
					next.Actor = installationActor(t, now)
				}
				if _, err := store.RegisterInstallation(ctx, next, now); err != nil {
					t.Fatal(err)
				}
			}
			sessions := eligibleFunc(func(context.Context, id.ID, id.ID) (bool, error) {
				switch scenario {
				case "session_denied":
					return false, nil
				case "session_outage":
					return false, dependency
				case "rotation_during_check":
					rotate(false)
				case "expiry_during_check":
					clock.now = a.ExpiresAt
				case "revocation_during_check":
					if err := store.RetireSession(ctx, app.SessionRevocation{EventID: randomID(t), PrincipalID: a.Principal, SessionID: a.Session, Version: 2, Reason: "sign_out", OccurredAt: now}); err != nil {
						t.Fatal(err)
					}
				}
				return true, nil
			})
			gateway := gatewayFunc(func(c context.Context, address app.PushAddress, p id.ID) (app.PushDisposition, error) {
				calls++
				if deadline, ok := c.Deadline(); !ok || time.Until(deadline) > 5*time.Second {
					t.Fatal("unbounded send")
				}
				if address.Lease != lease || address.Spec.Token != m.Spec.Token || p != preview {
					t.Fatal("wrong send input")
				}
				switch scenario {
				case "invalid":
					return app.PushInvalidTarget, nil
				case "uncertain":
					return "", dependency
				case "unknown":
					return "invented", nil
				case "rotation_during_send":
					rotate(false)
					return app.PushInvalidTarget, nil
				case "rebind_during_send":
					rotate(true)
					return app.PushInvalidTarget, nil
				case "unregister_during_send":
					if _, err := store.UnregisterInstallation(ctx, a, m.ID, m.ProofDigest, 1, now); err != nil {
						t.Fatal(err)
					}
					return app.PushInvalidTarget, nil
				}
				return app.PushAccepted, nil
			})
			h := command.SendPushPreview{Targets: store, Sessions: sessions, Gateway: gateway, Clock: clock}
			switch scenario {
			case "missing":
				lease.InstallationID = randomID(t)
			case "stale":
				lease.Version = 2
			case "wrong_owner":
				lease.PrincipalID = randomID(t)
			case "disabled":
				if _, err := store.UnregisterInstallation(ctx, a, m.ID, m.ProofDigest, 1, now); err != nil {
					t.Fatal(err)
				}
			case "expired":
				clock.now = a.ExpiresAt
			}
			callCtx := ctx
			if scenario == "canceled" {
				var cancel context.CancelFunc
				callCtx, cancel = context.WithCancel(ctx)
				cancel()
			}
			result, err := h.Handle(callCtx, lease, preview)
			wantCalls := 1
			switch scenario {
			case "session_denied":
				wantCalls = 0
				if !errors.Is(err, app.ErrPushSession) {
					t.Fatal("negative session", err)
				}
			case "session_outage":
				wantCalls = 0
				if !errors.Is(err, dependency) {
					t.Fatal("outage became a negative answer", err)
				}
			case "rotation_during_check", "expiry_during_check", "revocation_during_check", "missing", "stale", "wrong_owner", "disabled", "expired":
				wantCalls = 0
				if !errors.Is(err, app.ErrPushTarget) {
					t.Fatal("stale target sent", err)
				}
			case "canceled":
				wantCalls = 0
				if !errors.Is(err, context.Canceled) {
					t.Fatal("canceled", err)
				}
			case "uncertain":
				if !errors.Is(err, dependency) {
					t.Fatal("lost acknowledgement", err)
				}
			case "unknown":
				if !errors.Is(err, app.ErrPushResponse) {
					t.Fatal("invalid response", err)
				}
			default:
				if err != nil {
					t.Fatal(err)
				}
			}
			if calls != wantCalls {
				t.Fatal("automatic replay or unexpected send", calls)
			}
			if result.Retired != (scenario == "invalid") {
				t.Fatal("retired newer registration", scenario)
			}
			if scenario == "invalid" {
				current, err := store.GetInstallation(ctx, a, m.ID, m.ProofDigest, now)
				if err != nil || current.Snapshot().Enabled || current.Snapshot().Version != 2 || current.Snapshot().Spec.Token != "" {
					t.Fatal("token not erased atomically", err)
				}
				if retired, err := store.RetirePushTarget(ctx, lease, now); err != nil || retired {
					t.Fatal("repeated retirement", err)
				}
				// The target reservation is released with token erasure.
				other := installationMutation(t, installationActor(t, now))
				other.Spec = m.Spec
				if _, err := store.RegisterInstallation(ctx, other, now); err != nil {
					t.Fatal("target still reserved", err)
				}
			} else if scenario == "accepted" || scenario == "uncertain" || scenario == "unknown" || scenario == "session_denied" || scenario == "session_outage" {
				if address, err := store.PushTarget(ctx, lease, now); err != nil || address.Spec.Token != m.Spec.Token {
					t.Fatal("non-definitive result erased token", err)
				}
			} else if scenario == "rotation_during_send" || scenario == "rebind_during_send" {
				current, err := store.GetInstallation(ctx, a, m.ID, m.ProofDigest, now)
				if err != nil || !current.Snapshot().Enabled || current.Snapshot().Version != 2 || current.Snapshot().Spec.Token == m.Spec.Token {
					t.Fatal("late rejection damaged replacement", err)
				}
			}
		})
	}
	// Concurrent responses for the same snapshot can retire it only once.
	now := time.Now().UTC().Truncate(time.Microsecond)
	a := installationActor(t, now)
	m := installationMutation(t, a)
	if _, err := store.RegisterInstallation(ctx, m, now); err != nil {
		t.Fatal(err)
	}
	lease := app.PushLease{InstallationID: m.ID, PrincipalID: a.Principal, SessionID: a.Session, Version: 1}
	wins := make(chan bool, 12)
	var group sync.WaitGroup
	for range 12 {
		group.Go(func() {
			changed, err := store.RetirePushTarget(ctx, lease, now)
			if err != nil {
				t.Error(err)
			}
			wins <- changed
		})
	}
	group.Wait()
	n := 0
	for range 12 {
		if <-wins {
			n++
		}
	}
	if n != 1 {
		t.Fatal("retirement contention", n)
	}
}

func TestPushProviders(t *testing.T) {
	t.Run("memory", func(t *testing.T) {
		pushConformance(t, memory.New())
		sessionStatusConformance(t, identitymemory.New(id.Random{}))
	})
	if url := os.Getenv("TEST_POSTGRES_URL"); url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		t.Run("postgres", func(t *testing.T) {
			pool, err := storage.Open(context.Background(), storage.Config{URL: url})
			if err != nil {
				t.Fatal(err)
			}
			defer pool.Close()
			pushConformance(t, pg.New(pool))
			sessions, err := identitypg.New(pool, id.Random{})
			if err != nil {
				t.Fatal(err)
			}
			sessionStatusConformance(t, sessions)
			t.Run("retirement_rollback", func(t *testing.T) {
				ctx := context.Background()
				now := time.Now().UTC().Truncate(time.Microsecond)
				a := installationActor(t, now)
				m := installationMutation(t, a)
				store := pg.New(pool)
				if _, err := store.RegisterInstallation(ctx, m, now); err != nil {
					t.Fatal(err)
				}
				lease := app.PushLease{InstallationID: m.ID, PrincipalID: a.Principal, SessionID: a.Session, Version: 1}
				func() {
					if _, err := pool.Exec(ctx, `ALTER TABLE notifications.installations ADD CONSTRAINT fail_push_retirement CHECK(false) NOT VALID`); err != nil {
						t.Fatal(err)
					}
					defer func() {
						if _, err := pool.Exec(ctx, `ALTER TABLE notifications.installations DROP CONSTRAINT fail_push_retirement`); err != nil {
							t.Error(err)
						}
					}()
					if changed, err := store.RetirePushTarget(ctx, lease, now); err == nil || changed {
						t.Fatal("failed transaction acknowledged", err)
					}
				}()
				if address, err := store.PushTarget(ctx, lease, now); err != nil || address.Spec.Token != m.Spec.Token {
					t.Fatal("rollback lost target", err)
				}
				other := installationMutation(t, installationActor(t, now))
				other.Spec = m.Spec
				if _, err := store.RegisterInstallation(ctx, other, now); !errors.Is(err, app.ErrInstallationTarget) {
					t.Fatal("rollback released reservation", err)
				}
				if changed, err := store.RetirePushTarget(ctx, lease, now); err != nil || !changed {
					t.Fatal("explicit cleanup after rollback", err)
				}
			})
		})
	}
}

func TestPushCapabilityAndRevocationBeforeCleanup(t *testing.T) {
	ctx := context.Background()
	now := time.Now().UTC().Truncate(time.Microsecond)
	clock := &pushClock{now}
	sessions := identitymemory.New(id.Random{})
	session, refresh := issuedPushSession(t, sessions, now)
	v := session.Snapshot()
	registry := capability.NewRegistry()
	if err := registry.Register(identitycap.ProvideSessionEligibility(identityquery.SessionEligibility{Store: sessions, Clock: clock})); err != nil {
		t.Fatal(err)
	}
	registry.Freeze()
	request := map[string]any{"principal_id": v.PrincipalID, "session_id": v.ID}
	var response struct {
		Eligible *bool `json:"eligible"`
	}
	if err := registry.Client("api").Invoke(ctx, identitycap.SessionEligibilityName, request, &response); !errors.Is(err, capability.ErrCaller) {
		t.Fatal("wrong caller", err)
	}
	for _, bad := range []any{map[string]any{"principal_id": v.PrincipalID}, map[string]any{"principal_id": v.PrincipalID, "session_id": v.ID, "admin": true}} {
		if err := registry.Client("notifications").Invoke(ctx, identitycap.SessionEligibilityName, bad, &response); err == nil {
			t.Fatal("malformed eligibility request accepted")
		}
	}
	store := memory.New()
	actor := app.InstallationActor{Principal: v.PrincipalID, Session: v.ID, ExpiresAt: v.ExpiresAt}
	m := installationMutation(t, actor)
	if _, err := store.RegisterInstallation(ctx, m, now); err != nil {
		t.Fatal(err)
	}
	lease := app.PushLease{InstallationID: m.ID, PrincipalID: v.PrincipalID, SessionID: v.ID, Version: 1}
	calls := 0
	gateway := gatewayFunc(func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error) {
		calls++
		return app.PushAccepted, nil
	})
	h, err := notifications.NewPushPreview(store, module.Runtime{Clock: clock, Capabilities: registry}, gateway)
	if err != nil {
		t.Fatal(err)
	}
	clock.now = now.Add(16 * time.Minute)
	if _, err := h.Handle(ctx, lease, randomID(t)); err != nil || calls != 1 {
		t.Fatal("access expiry incorrectly disabled live session", err)
	}
	if err := sessions.Revoke(ctx, refresh, clock.now, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	// Do not apply the Identity outbox to Notifications: the registry is still enabled.
	if _, err := store.PushTarget(ctx, lease, clock.now); err != nil {
		t.Fatal("expected cleanup lag", err)
	}
	if _, err := h.Handle(ctx, lease, randomID(t)); !errors.Is(err, app.ErrPushSession) || calls != 1 {
		t.Fatal("revocation lag allowed push", err)
	}
	if _, err := notifications.NewPushPreview(store, module.Runtime{Clock: clock, Capabilities: capability.NewRegistry()}, gateway); !errors.Is(err, capability.ErrMissing) {
		t.Fatal("missing dependency", err)
	}
	if _, err := notifications.NewPushPreview(store, module.Runtime{Clock: clock, Capabilities: registry}, nil); !errors.Is(err, capability.ErrInvalid) {
		t.Fatal("missing gateway", err)
	}
	for _, bad := range []any{map[string]any{}, map[string]any{"eligible": nil}, map[string]any{"eligible": "false"}} {
		r := capability.NewRegistry()
		if err := r.Register(capability.Provision{Name: identitycap.SessionEligibilityName, Callers: []string{"notifications"}, Handler: func(context.Context, json.RawMessage) (any, error) { return bad, nil }}); err != nil {
			t.Fatal(err)
		}
		if _, err := (capabilities.Client{Invoker: r.Client("notifications")}).SessionEligible(ctx, v.PrincipalID, v.ID); err == nil {
			t.Fatal("malformed answer accepted")
		}
	}
	secret := "private-push-address"
	address := app.PushAddress{Lease: lease, Spec: m.Spec}
	address.Spec.Token = secret
	if output := fmt.Sprintf("%v %#v %v %#v", address, address, lease, lease); strings.Contains(output, secret) || strings.Contains(output, v.ID.String()) {
		t.Fatal("private dispatch context printed")
	}
}
