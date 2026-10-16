package domain

import (
	"strings"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func TestCanonicalIdentityAndSessionTransitions(t *testing.T) {
	if login, err := Login("  Alex.Example  "); err != nil || login != "alex.example" {
		t.Fatal(login, err)
	}
	for _, value := range []string{"ab", "a b", "../../root", "éxample"} {
		if _, err := Login(value); err == nil {
			t.Fatal("invalid login admitted")
		}
	}
	if Password("short") == nil || Password(strings.Repeat("a", 257)) == nil {
		t.Fatal("password admission failed")
	}
	now := time.Date(2026, 10, 13, 0, 0, 0, 0, time.UTC)
	identity := id.ID("00000000-0000-4000-8000-000000000001")
	s, err := NewSession(identity, identity, strings.Repeat("a", 64), strings.Repeat("b", 64), now)
	if err != nil {
		t.Fatal(err)
	}
	if s.Active(now.Add(-time.Second)) || s.Renewable(now.Add(-time.Second)) {
		t.Fatal("grant accepted before issuance")
	}
	if _, err := s.Rotate(s.Snapshot().AccessDigest, strings.Repeat("c", 64), now.Add(time.Second)); err == nil {
		t.Fatal("rotation reused access secret")
	}
	next, err := s.Revoke(now.Add(time.Hour))
	if err != nil || next.Active(now) || next.Renewable(now) {
		t.Fatal("revocation state failed", err)
	}
	if _, err := RestoreSession(next.Snapshot()); err != nil {
		t.Fatal("revoked state failed restore", err)
	}
	corrupt := s.Snapshot()
	corrupt.ExpiresAt = corrupt.ExpiresAt.Add(time.Hour)
	if _, err := RestoreSession(corrupt); err == nil {
		t.Fatal("extended lifetime restored")
	}
}
