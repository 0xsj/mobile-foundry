package domain

import (
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"regexp"
	"strings"
	"time"
)

var emailPattern = regexp.MustCompile(`^[a-z0-9][a-z0-9.!#$%&'*+/=?^_` + "`" + `{|}~-]{0,63}@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$`)

// Email is an explicit ASCII mailbox policy. It is separate from Login.
func Email(value string) (string, error) {
	value = strings.ToLower(strings.TrimSpace(value))
	if len(value) > 254 || !emailPattern.MatchString(value) || strings.Contains(value, "..") {
		return "", ErrInput
	}
	parts := strings.Split(value, "@")
	if len(parts) != 2 || strings.HasSuffix(parts[0], ".") {
		return "", ErrInput
	}
	for _, label := range strings.Split(parts[1], ".") {
		if len(label) > 63 {
			return "", ErrInput
		}
	}
	return value, nil
}

const VerifyEmail = "verify_email"
const ResetPassword = "reset_password"

type Contact struct {
	PrincipalID id.ID
	Email       string
	VerifiedAt  time.Time
}

func (c Contact) Validate() error {
	email, err := Email(c.Email)
	if err != nil || email != c.Email || !c.PrincipalID.Valid() || !canonical(c.VerifiedAt) {
		return ErrState
	}
	return nil
}

type Challenge struct {
	PrincipalID                            id.ID
	Purpose, Email, Digest, CredentialHash string
	CreatedAt, ExpiresAt                   time.Time
}

func (c Challenge) Validate() error {
	email, e := Email(c.Email)
	if e != nil || email != c.Email || !c.PrincipalID.Valid() || !digestPattern.MatchString(c.Digest) || c.CredentialHash == "" || len(c.CredentialHash) > 512 || !canonical(c.CreatedAt) || !canonical(c.ExpiresAt) {
		return ErrState
	}
	if c.Purpose != VerifyEmail && c.Purpose != ResetPassword {
		return ErrState
	}
	if !c.ExpiresAt.Equal(c.CreatedAt.Add(20 * time.Minute)) {
		return ErrState
	}
	return nil
}
func (c Challenge) Active(now time.Time) bool {
	return !now.Before(c.CreatedAt) && now.Before(c.ExpiresAt)
}
func (Challenge) String() string   { return "identity.Challenge{redacted}" }
func (Challenge) GoString() string { return "identity.Challenge{redacted}" }
func (p Principal) WithPassword(hash string) (Principal, error) {
	v := p.Snapshot()
	v.PasswordHash = hash
	return RestorePrincipal(v)
}
