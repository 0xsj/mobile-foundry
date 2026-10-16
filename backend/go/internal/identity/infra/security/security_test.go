package security

import (
	"context"
	"fmt"
	"strings"
	"testing"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
)

func TestPasswordStorageAndBoundedAdmission(t *testing.T) {
	ctx := context.Background()
	p, err := NewPasswords()
	if err != nil {
		t.Fatal(err)
	}
	password := "a long private passphrase"
	one, err := p.Hash(ctx, password)
	if err != nil {
		t.Fatal(err)
	}
	two, err := p.Hash(ctx, password)
	if err != nil || one == two || strings.Contains(one, password) {
		t.Fatal("password salt/storage failed", err)
	}
	for _, candidate := range []struct {
		value string
		match bool
	}{{password, true}, {"incorrect private passphrase", false}} {
		match, err := p.Verify(ctx, one, candidate.value)
		if err != nil || match != candidate.match {
			t.Fatal(match, err)
		}
	}
	for _, hash := range []string{"plaintext", strings.Replace(one, "m=19456", "m=4294967295", 1), one + "$extra"} {
		if _, err := p.Verify(ctx, hash, password); err == nil {
			t.Fatal("unbounded or malformed stored hash accepted")
		}
	}
	p.work <- struct{}{}
	p.work <- struct{}{}
	if _, err := p.Hash(ctx, password); err != app.ErrBusy {
		t.Fatal("work budget ignored", err)
	}
	<-p.work
	<-p.work
	cancelled, cancel := context.WithCancel(ctx)
	cancel()
	if _, err := p.Hash(cancelled, password); err == nil {
		t.Fatal("cancelled hash accepted")
	}
}
func TestOpaqueTokensPurposeAndEntropy(t *testing.T) {
	ctx := context.Background()
	tokens := Tokens{}
	a, digest, err := tokens.Issue(ctx, "access")
	if err != nil || len(a) != 46 || len(digest) != 64 || strings.Contains(digest, a) {
		t.Fatal(err)
	}
	other, _, _ := tokens.Issue(ctx, "access")
	if other == a {
		t.Fatal("token collision")
	}
	if d, err := tokens.Digest(ctx, a, "access"); err != nil || d != digest {
		t.Fatal(err)
	}
	if _, err := tokens.Digest(ctx, a, "refresh"); err == nil {
		t.Fatal("access accepted as refresh")
	}
	for _, value := range []string{a + "=", a[:45], "a1." + strings.Repeat("!", 43)} {
		if _, err := tokens.Digest(ctx, value, "access"); err == nil {
			t.Fatal("malformed token accepted")
		}
	}
	gate := NewGate()
	for range 12 {
		if err := gate.Allow(ctx, "source"); err != nil {
			t.Fatal(err)
		}
	}
	if gate.Allow(ctx, "source") != app.ErrBusy {
		t.Fatal("attempt limit ignored")
	}
	if gate.Allow(ctx, "other") != nil {
		t.Fatal("other source blocked")
	}
	if strings.Contains(fmt.Sprintf("%+v", pForFormat()), "private") {
		t.Fatal("private formatter")
	}
}
func pForFormat() *Passwords { return &Passwords{dummy: "private", work: make(chan struct{}, 2)} }
