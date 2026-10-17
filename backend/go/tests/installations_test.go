package tests

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/memory"
	pg "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/security"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"os"
	"strings"
	"sync"
	"testing"
	"time"
)

func installationProof(t *testing.T) (string, string) {
	t.Helper()
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		t.Fatal(err)
	}
	key := "i1." + base64.RawURLEncoding.EncodeToString(bytes)
	hash, err := (security.InstallationProofs{}).Digest(context.Background(), key)
	if err != nil {
		t.Fatal(err)
	}
	return key, hash
}
func installationActor(t *testing.T, now time.Time) app.InstallationActor {
	t.Helper()
	return app.InstallationActor{Principal: randomID(t), Session: randomID(t), ExpiresAt: now.Add(time.Hour)}
}
func installationMutation(t *testing.T, a app.InstallationActor) app.InstallationMutation {
	t.Helper()
	_, hash := installationProof(t)
	return app.InstallationMutation{Actor: a, ID: randomID(t), RegistrationID: randomID(t), ProofDigest: hash, Spec: domain.InstallationSpec{Route: domain.PushRoute{Provider: "apns", Application: "catalog.ios", Environment: "sandbox"}, Token: "private-target-" + randomID(t).String()}}
}
func installationsConformance(t *testing.T, s app.Installations) {
	t.Helper()
	ctx := context.Background()
	now := time.Now().UTC().Truncate(time.Microsecond)
	a := installationActor(t, now)
	m := installationMutation(t, a)
	var group sync.WaitGroup
	results := make(chan error, 12)
	for range 12 {
		group.Go(func() { _, err := s.RegisterInstallation(ctx, m, now); results <- err })
	}
	group.Wait()
	wins := 0
	for range 12 {
		err := <-results
		if err == nil {
			wins++
		} else if !errors.Is(err, app.ErrInstallationConflict) {
			t.Fatal(err)
		}
	}
	if wins != 1 {
		t.Fatal("create contention", wins)
	}
	first, err := s.GetInstallation(ctx, a, m.ID, m.ProofDigest, now)
	if err != nil || first.Snapshot().Version != 1 {
		t.Fatal("create", err)
	}
	_, wrong := installationProof(t)
	if _, err := s.GetInstallation(ctx, a, m.ID, wrong, now); !errors.Is(err, app.ErrInstallationMissing) {
		t.Fatal("proof bypass", err)
	}
	reused := m
	reused.Expected = 1
	if _, err := s.RegisterInstallation(ctx, reused, now); !errors.Is(err, app.ErrInstallationConflict) {
		t.Fatal("registration checkpoint reused", err)
	}
	rotate := m
	rotate.Expected = 1
	rotate.RegistrationID = randomID(t)
	rotate.Spec.Token += "-rotated"
	second, err := s.RegisterInstallation(ctx, rotate, now)
	if err != nil || second.Snapshot().Version != 2 {
		t.Fatal("rotation", err)
	}
	if _, err := s.UnregisterInstallation(ctx, a, m.ID, m.ProofDigest, 1, now); !errors.Is(err, app.ErrInstallationConflict) {
		t.Fatal("stale cleanup", err)
	}
	b := installationActor(t, now)
	rebind := rotate
	rebind.Actor = b
	rebind.Expected = 2
	rebind.RegistrationID = randomID(t)
	third, err := s.RegisterInstallation(ctx, rebind, now)
	if err != nil || third.Snapshot().Version != 3 {
		t.Fatal("rebind", err)
	}
	if _, err := s.UnregisterInstallation(ctx, a, m.ID, m.ProofDigest, 3, now); !errors.Is(err, app.ErrInstallationBinding) {
		t.Fatal("old session unbound replacement", err)
	}
	duplicate := installationMutation(t, a)
	duplicate.Spec = rebind.Spec
	if _, err := s.RegisterInstallation(ctx, duplicate, now); !errors.Is(err, app.ErrInstallationTarget) {
		t.Fatal("duplicate target", err)
	}
	if _, err := s.GetInstallation(ctx, a, duplicate.ID, duplicate.ProofDigest, now); !errors.Is(err, app.ErrInstallationMissing) {
		t.Fatal("failed create left record", err)
	}
	old := app.SessionRevocation{EventID: randomID(t), PrincipalID: a.Principal, SessionID: a.Session, Version: 2, Reason: "sign_out", OccurredAt: now}
	if err := s.RetireSession(ctx, old); err != nil {
		t.Fatal(err)
	}
	still, err := s.GetInstallation(ctx, b, m.ID, m.ProofDigest, now)
	if err != nil || still.Snapshot() != third.Snapshot() {
		t.Fatal("late revoke cleared new binding", err)
	}
	late := installationMutation(t, a)
	if _, err := s.RegisterInstallation(ctx, late, now); !errors.Is(err, app.ErrInstallationSession) {
		t.Fatal("late registration resurrected revoked session", err)
	}
	if err := s.RetireSession(ctx, old); err != nil {
		t.Fatal("source replay", err)
	}
	changed := old
	changed.Reason = "refresh_reuse"
	if err := s.RetireSession(ctx, changed); !errors.Is(err, app.ErrReplay) {
		t.Fatal("conflicting event", err)
	}
	revoke := old
	revoke.EventID = randomID(t)
	revoke.PrincipalID = b.Principal
	revoke.SessionID = b.Session
	revoke.Reason = "refresh_reuse"
	if err := s.RetireSession(ctx, revoke); err != nil {
		t.Fatal(err)
	}
	c := installationActor(t, now)
	disabled, err := s.GetInstallation(ctx, c, m.ID, m.ProofDigest, now)
	if err != nil || disabled.Snapshot().Enabled || disabled.Snapshot().Spec.Token != "" || disabled.Snapshot().Version != 4 {
		t.Fatal("revoke did not erase routing token", err)
	}
	free := installationMutation(t, c)
	free.Spec = rebind.Spec
	if _, err := s.RegisterInstallation(ctx, free, now); err != nil {
		t.Fatal("retired token stayed reserved", err)
	}
	off, err := s.UnregisterInstallation(ctx, c, free.ID, free.ProofDigest, 1, now)
	if err != nil {
		t.Fatal(err)
	}
	again, err := s.UnregisterInstallation(ctx, c, free.ID, free.ProofDigest, 1, now)
	if err != nil || again.Snapshot() != off.Snapshot() {
		t.Fatal("repeat unregister", err)
	}
	// A lost local installation proof does not permanently reserve an expired target.
	expired := installationMutation(t, installationActor(t, now))
	if _, err := s.RegisterInstallation(ctx, expired, now); err != nil {
		t.Fatal(err)
	}
	later := now.Add(2 * time.Hour)
	freshActor := installationActor(t, later)
	fresh := installationMutation(t, freshActor)
	fresh.Spec = expired.Spec
	if _, err := s.RegisterInstallation(ctx, fresh, later); err != nil {
		t.Fatal("expired target not reclaimed", err)
	}
	retired, err := s.GetInstallation(ctx, freshActor, expired.ID, expired.ProofDigest, later)
	if err != nil || retired.Snapshot().Enabled || retired.Snapshot().Spec.Token != "" {
		t.Fatal("expired token not cleared", err)
	}
	canceled, cancel := context.WithCancel(ctx)
	cancel()
	if _, err := s.RegisterInstallation(canceled, installationMutation(t, freshActor), later); !errors.Is(err, context.Canceled) {
		t.Fatal("cancellation", err)
	}
}
func TestInstallationProviders(t *testing.T) {
	t.Run("memory", func(t *testing.T) { installationsConformance(t, memory.New()) })
	if url := os.Getenv("TEST_POSTGRES_URL"); url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		t.Run("postgres", func(t *testing.T) {
			ctx := context.Background()
			pool, err := storage.Open(ctx, storage.Config{URL: url})
			if err != nil {
				t.Fatal(err)
			}
			defer pool.Close()
			s := pg.New(pool)
			installationsConformance(t, s)
			now := time.Now().UTC().Truncate(time.Microsecond)
			a := installationActor(t, now)
			m := installationMutation(t, a)
			if _, err := s.RegisterInstallation(ctx, m, now); err != nil {
				t.Fatal(err)
			}
			r := app.SessionRevocation{EventID: randomID(t), PrincipalID: a.Principal, SessionID: a.Session, Version: 2, Reason: "sign_out", OccurredAt: now}
			for _, table := range []string{"installations", "revoked_installation_sessions", "installation_inbox"} {
				if _, err := pool.Exec(ctx, "ALTER TABLE notifications."+table+" ADD CONSTRAINT fail_installation CHECK(false) NOT VALID"); err != nil {
					t.Fatal(err)
				}
				err = s.RetireSession(ctx, r)
				if _, drop := pool.Exec(ctx, "ALTER TABLE notifications."+table+" DROP CONSTRAINT fail_installation"); drop != nil {
					t.Fatal(drop)
				}
				if err == nil {
					t.Fatal("forced revoke committed", table)
				}
				current, err := s.GetInstallation(ctx, a, m.ID, m.ProofDigest, now)
				if err != nil || !current.Snapshot().Enabled || current.Snapshot().Version != 1 {
					t.Fatal("partial retirement/tombstone", err)
				}
				var n int
				if err := pool.QueryRow(ctx, `SELECT count(*) FROM notifications.installation_inbox WHERE event_id=$1`, r.EventID.String()).Scan(&n); err != nil || n != 0 {
					t.Fatal("partial source marker", err)
				}
			}
			if err := s.RetireSession(ctx, r); err != nil {
				t.Fatal("retry after rollback", err)
			}
			var token string
			if err := pool.QueryRow(ctx, `SELECT token FROM notifications.installations WHERE id=$1`, m.ID.String()).Scan(&token); err != nil || token != "" {
				t.Fatal("persisted token retained", err)
			}
		})
	}
}
func TestInstallationPrivacyAndAdmission(t *testing.T) {
	key, _ := installationProof(t)
	for _, bad := range []string{"", key + "=", strings.Replace(key, "i1.", "a1.", 1)} {
		if _, err := (security.InstallationProofs{}).Digest(context.Background(), bad); err == nil {
			t.Fatal("bad key accepted")
		}
	}
	now := time.Now().UTC()
	m := installationMutation(t, installationActor(t, now))
	m.Spec.Token = "private-target-never-print"
	for _, value := range []any{m, m.Spec} {
		if strings.Contains(fmt.Sprintf("%v %#v", value, value), m.Spec.Token) {
			t.Fatal("secret formatter")
		}
	}
	for _, token := range []string{"", "has space", "has\nnewline", strings.Repeat("x", 4097)} {
		spec := m.Spec
		spec.Token = token
		if spec.Validate() == nil {
			t.Fatal("bad token admitted")
		}
	}
	for _, route := range []domain.PushRoute{{Provider: "other", Application: "app", Environment: "sandbox"}, {Provider: "fcm", Application: "app", Environment: "sandbox"}, {Provider: "apns", Application: "../topic", Environment: "production"}} {
		if route.Validate() == nil {
			t.Fatal("bad route admitted")
		}
	}
}
func TestInstallationAuthenticatedHTTPAndSessionRetirement(t *testing.T) {
	h, registry := filesHTTPRegistry(t)
	for _, name := range []string{"install-alex", "install-sam"} {
		status, _, _ := h.request("POST", "/v1/identity/register", "", `{"login":"`+name+`","display_name":"Person","password":"private test passphrase"}`, nil)
		if status != 201 {
			t.Fatal(status)
		}
	}
	a, b := login(t, h, "install-alex"), login(t, h, "install-sam")
	input := struct {
		AccessToken string `json:"access_token"`
	}{a.Access}
	var v1 struct {
		PrincipalID id.ID `json:"principal_id"`
	}
	if err := registry.Client("api").Invoke(context.Background(), "identity.credential.resolve.v1", input, &v1); err != nil || !v1.PrincipalID.Valid() {
		t.Fatal("v1 compatibility", err)
	}
	var v2 struct {
		PrincipalID      id.ID     `json:"principal_id"`
		SessionID        id.ID     `json:"session_id"`
		SessionExpiresAt time.Time `json:"session_expires_at"`
	}
	if err := registry.Client("api").Invoke(context.Background(), "identity.credential.resolve.v2", input, &v2); err != nil || v2.PrincipalID != v1.PrincipalID || !v2.SessionID.Valid() || !time.Now().Before(v2.SessionExpiresAt) {
		t.Fatal("v2 session evidence", err)
	}
	if err := registry.Client("notifications").Invoke(context.Background(), "identity.credential.resolve.v2", input, &v2); err == nil {
		t.Fatal("non-edge caller resolved credentials")
	}
	proof, _ := installationProof(t)
	wrong, _ := installationProof(t)
	identity := randomID(t).String()
	registration := randomID(t).String()
	token := "private-provider-token"
	body := func(reg, token string, create bool) string {
		v := map[string]string{"registration_id": reg, "provider": "apns", "application": "catalog.ios", "environment": "sandbox", "token": token}
		if create {
			v["installation_id"] = identity
		}
		wire, _ := json.Marshal(v)
		return string(wire)
	}
	headers := map[string]string{"X-Installation-Key": proof}
	path := "/v1/installations/" + identity
	status, hdr, wire := h.request("POST", "/v1/installations", a.Access, body(registration, token, true), headers)
	if status != 201 || hdr.Get("ETag") != `"1"` || hdr.Get("Location") != path {
		t.Fatal("create", status, string(wire))
	}
	for _, secret := range []string{proof, token, "proof_digest", "principal_id", "session_id"} {
		if strings.Contains(string(wire), secret) {
			t.Fatal("private registration data exposed")
		}
	}
	if status, _, _ := h.request("GET", path, b.Access, "", map[string]string{"X-Installation-Key": wrong}); status != 404 {
		t.Fatal("ID alone accessed installation", status)
	}
	status, _, wire = h.request("GET", path, b.Access, "", headers)
	if status != 200 || !strings.Contains(string(wire), `"current_binding":false`) {
		t.Fatal("proof-holder readback", status)
	}
	update := map[string]string{"X-Installation-Key": proof, "If-Match": `"1"`}
	status, _, wire = h.request("PUT", path, b.Access, body(randomID(t).String(), token+"-rotated", false), update)
	if status != 200 || !strings.Contains(string(wire), `"current_binding":true`) {
		t.Fatal("rebind", status, string(wire))
	}
	if status, _, _ := h.request("DELETE", path, a.Access, "", map[string]string{"X-Installation-Key": proof, "If-Match": `"2"`}); status != 403 {
		t.Fatal("stale session cleanup", status)
	}
	if status, _, _ := h.request("PUT", path, b.Access, body(randomID(t).String(), token, false), update); status != 409 {
		t.Fatal("stale update", status)
	}
	if status, _, _ := h.request("PUT", path, b.Access, body(randomID(t).String(), token, false), headers); status != 428 {
		t.Fatal("missing precondition", status)
	}
	status, _, wire = h.request("POST", "/v1/identity/renew", "", `{"refresh_token":"`+b.Refresh+`"}`, nil)
	if status != 200 {
		t.Fatal(status)
	}
	var grant struct {
		Access  string `json:"access_token"`
		Refresh string `json:"refresh_token"`
	}
	if json.Unmarshal(wire, &grant) != nil {
		t.Fatal("renew response")
	}
	status, _, wire = h.request("GET", path, grant.Access, "", headers)
	if status != 200 || !strings.Contains(string(wire), `"current_binding":true`) {
		t.Fatal("renewal changed installation session", status, string(wire))
	}
	if status, _, _ := h.request("POST", "/v1/identity/sign-out", "", `{"refresh_token":"`+grant.Refresh+`"}`, nil); status != 204 {
		t.Fatal(status)
	}
	fresh := login(t, h, "install-sam")
	deadline := time.Now().Add(3 * time.Second)
	for {
		status, _, wire = h.request("GET", path, fresh.Access, "", headers)
		if status != 200 {
			t.Fatal(status, string(wire))
		}
		if strings.Contains(string(wire), `"registered":false`) {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("sign-out event did not retire registration")
		}
		time.Sleep(10 * time.Millisecond)
	}
	for _, bad := range []map[string]string{{"X-Installation-Key": proof, "Idempotency-Key": "unsupported"}, {"X-Installation-Key": "bad"}} {
		if status, _, _ := h.request("GET", path, fresh.Access, "", bad); status != 400 {
			t.Fatal("bad installation admission", status)
		}
	}
	if status, _, _ := h.request("HEAD", path, fresh.Access, "", headers); status != 200 {
		t.Fatal("HEAD", status)
	}
	if status, _, _ := h.request("GET", path, "", "", headers); status != 401 {
		t.Fatal("missing authentication", status)
	}
}
