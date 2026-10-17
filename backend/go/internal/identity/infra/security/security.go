package security

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"strings"
	"sync"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"golang.org/x/crypto/argon2"
)

const parameters = "argon2id$v=19$m=19456,t=2,p=1"

type Passwords struct {
	work  chan struct{}
	dummy string
}

func NewPasswords() (*Passwords, error) {
	p := &Passwords{work: make(chan struct{}, 2)}
	hash, err := p.Hash(context.Background(), "unused-dummy-credential")
	if err != nil {
		return nil, err
	}
	p.dummy = hash
	return p, nil
}
func (p *Passwords) calculate(ctx context.Context, password string, salt []byte) ([]byte, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	select {
	case p.work <- struct{}{}:
	default:
		return nil, app.ErrBusy
	}
	defer func() { <-p.work }()
	key := argon2.IDKey([]byte(password), salt, 2, 19456, 1, 32)
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	return key, nil
}
func (p *Passwords) Hash(ctx context.Context, password string) (string, error) {
	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		return "", domain.ErrState.WithCause(err)
	}
	key, err := p.calculate(ctx, password, salt)
	if err != nil {
		return "", err
	}
	return "$" + parameters + "$" + base64.RawStdEncoding.EncodeToString(salt) + "$" + base64.RawStdEncoding.EncodeToString(key), nil
}
func (p *Passwords) Verify(ctx context.Context, hash, password string) (bool, error) {
	parts := strings.Split(hash, "$")
	if len(parts) != 6 || strings.Join(parts[1:4], "$") != parameters {
		return false, domain.ErrState
	}
	salt, e1 := base64.RawStdEncoding.Strict().DecodeString(parts[4])
	expected, e2 := base64.RawStdEncoding.Strict().DecodeString(parts[5])
	if e1 != nil || e2 != nil || len(salt) != 16 || len(expected) != 32 {
		return false, domain.ErrState
	}
	actual, err := p.calculate(ctx, password, salt)
	if err != nil {
		return false, err
	}
	return subtle.ConstantTimeCompare(actual, expected) == 1, nil
}
func (p *Passwords) Dummy(ctx context.Context, password string) error {
	_, err := p.Verify(ctx, p.dummy, password)
	return err
}

type Tokens struct{}

func prefix(kind string) string {
	if kind == domain.VerifyEmail {
		return "e1."
	}
	if kind == domain.ResetPassword {
		return "p1."
	}
	if kind == "access" {
		return "a1."
	}
	if kind == "refresh" {
		return "r1."
	}
	return ""
}
func (Tokens) Issue(ctx context.Context, kind string) (string, string, error) {
	if err := ctx.Err(); err != nil {
		return "", "", err
	}
	if prefix(kind) == "" {
		return "", "", domain.ErrState
	}
	var bytes [32]byte
	if _, err := rand.Read(bytes[:]); err != nil {
		return "", "", domain.ErrState.WithCause(err)
	}
	value := prefix(kind) + base64.RawURLEncoding.EncodeToString(bytes[:])
	digest, err := (Tokens{}).Digest(ctx, value, kind)
	return value, digest, err
}
func (Tokens) Digest(ctx context.Context, value, kind string) (string, error) {
	if err := ctx.Err(); err != nil {
		return "", err
	}
	if prefix(kind) == "" || len(value) != 46 || !strings.HasPrefix(value, prefix(kind)) {
		return "", domain.ErrCredential
	}
	decoded, err := base64.RawURLEncoding.Strict().DecodeString(value[3:])
	if err != nil || len(decoded) != 32 {
		return "", domain.ErrCredential
	}
	digest := sha256.Sum256([]byte(value))
	return hex.EncodeToString(digest[:]), nil
}

// Gate is an edge-local budget, independent of login existence. No proxy headers
// are trusted. It bounds its IP map; distributed abuse controls remain separate.
type Gate struct {
	mu      sync.Mutex
	entries map[string]bucket
}
type bucket struct {
	tokens float64
	at     time.Time
}

func NewGate() *Gate { return &Gate{entries: make(map[string]bucket)} }
func (g *Gate) Allow(ctx context.Context, address string) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	now := time.Now()
	b, found := g.entries[address]
	if !found {
		if len(g.entries) >= 1000 {
			for key, value := range g.entries {
				if now.Sub(value.at) > time.Minute {
					delete(g.entries, key)
				}
			}
			if len(g.entries) >= 1000 {
				return app.ErrBusy
			}
		}
		b = bucket{tokens: 12, at: now}
	} else {
		b.tokens += now.Sub(b.at).Seconds()
		if b.tokens > 12 {
			b.tokens = 12
		}
	}
	b.at = now
	if b.tokens < 1 {
		g.entries[address] = b
		return app.ErrBusy
	}
	b.tokens--
	g.entries[address] = b
	return nil
}
func (Passwords) String() string   { return "identity.Passwords{redacted}" }
func (Passwords) GoString() string { return "identity.Passwords{redacted}" }
