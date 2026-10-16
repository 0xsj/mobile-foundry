package domain

import (
	"regexp"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const AccessLifetime = 15 * time.Minute
const SessionLifetime = 30 * 24 * time.Hour

var (
	ErrInput      = apperrors.New(apperrors.Invalid, "identity.invalid_input", "invalid identity input").WithPublic("Check the login, display name and password.")
	ErrState      = apperrors.New(apperrors.Internal, "identity.invalid_state", "invalid identity state")
	ErrCredential = apperrors.New(apperrors.Unauthenticated, "identity.invalid_credential", "credential is not accepted").WithPublic("Sign in again to continue.")
	loginPattern  = regexp.MustCompile(`^[a-z0-9][a-z0-9._@+-]{2,127}$`)
	digestPattern = regexp.MustCompile(`^[a-f0-9]{64}$`)
)

func Login(value string) (string, error) {
	value = strings.ToLower(strings.TrimSpace(value))
	if !loginPattern.MatchString(value) {
		return "", ErrInput
	}
	return value, nil
}
func DisplayName(value string) (string, error) {
	value = strings.TrimSpace(value)
	if !utf8.ValidString(value) || utf8.RuneCountInString(value) < 1 || utf8.RuneCountInString(value) > 80 {
		return "", ErrInput
	}
	for _, r := range value {
		if unicode.IsControl(r) {
			return "", ErrInput
		}
	}
	return value, nil
}
func Password(value string) error {
	if !utf8.ValidString(value) || utf8.RuneCountInString(value) < 12 || len(value) > 256 {
		return ErrInput
	}
	return nil
}
func canonical(value time.Time) bool {
	return value.Location() == time.UTC && value.Year() >= 1970 && value.Year() <= 9999 && value.Equal(value.Truncate(time.Microsecond))
}
func Time(now time.Time) time.Time { return now.UTC().Truncate(time.Microsecond) }

type PrincipalSnapshot struct {
	ID                               id.ID
	Login, DisplayName, PasswordHash string
	CreatedAt                        time.Time
}
type Principal struct{ snapshot PrincipalSnapshot }

func NewPrincipal(identity id.ID, login, name, hash string, now time.Time) (Principal, error) {
	login, err := Login(login)
	if err != nil {
		return Principal{}, err
	}
	name, err = DisplayName(name)
	if err != nil {
		return Principal{}, err
	}
	return RestorePrincipal(PrincipalSnapshot{identity, login, name, hash, Time(now)})
}
func RestorePrincipal(s PrincipalSnapshot) (Principal, error) {
	login, e1 := Login(s.Login)
	name, e2 := DisplayName(s.DisplayName)
	if !s.ID.Valid() || e1 != nil || e2 != nil || login != s.Login || name != s.DisplayName || s.PasswordHash == "" || len(s.PasswordHash) > 512 || !canonical(s.CreatedAt) {
		return Principal{}, ErrState
	}
	return Principal{s}, nil
}
func (p Principal) Snapshot() PrincipalSnapshot { return p.snapshot }
func (Principal) String() string                { return "identity.Principal{redacted}" }
func (Principal) GoString() string              { return "identity.Principal{redacted}" }

type SessionSnapshot struct {
	ID, PrincipalID                                             id.ID
	Generation                                                  uint64
	AccessDigest, RefreshDigest                                 string
	CreatedAt, UpdatedAt, AccessExpiresAt, ExpiresAt, RevokedAt time.Time
}
type Session struct{ snapshot SessionSnapshot }

func NewSession(identity, principalID id.ID, access, refresh string, now time.Time) (Session, error) {
	now = Time(now)
	return RestoreSession(SessionSnapshot{ID: identity, PrincipalID: principalID, Generation: 1, AccessDigest: access, RefreshDigest: refresh, CreatedAt: now, UpdatedAt: now, AccessExpiresAt: now.Add(AccessLifetime), ExpiresAt: now.Add(SessionLifetime)})
}
func RestoreSession(s SessionSnapshot) (Session, error) {
	if !s.ID.Valid() || !s.PrincipalID.Valid() || s.Generation == 0 || s.Generation > 1<<53-1 || !digestPattern.MatchString(s.AccessDigest) || !digestPattern.MatchString(s.RefreshDigest) || s.AccessDigest == s.RefreshDigest || !canonical(s.CreatedAt) || !canonical(s.UpdatedAt) || !canonical(s.AccessExpiresAt) || !canonical(s.ExpiresAt) || s.UpdatedAt.Before(s.CreatedAt) || !s.ExpiresAt.Equal(s.CreatedAt.Add(SessionLifetime)) || !s.AccessExpiresAt.After(s.CreatedAt) || (s.RevokedAt.IsZero() && !s.AccessExpiresAt.After(s.UpdatedAt)) || s.AccessExpiresAt.After(s.ExpiresAt) || s.AccessExpiresAt.After(s.UpdatedAt.Add(AccessLifetime)) || (!s.RevokedAt.IsZero() && (!canonical(s.RevokedAt) || s.RevokedAt.Before(s.CreatedAt) || s.RevokedAt != s.UpdatedAt)) {
		return Session{}, ErrState
	}
	return Session{s}, nil
}
func (s Session) Snapshot() SessionSnapshot { return s.snapshot }
func (s Session) Active(now time.Time) bool {
	return !now.Before(s.snapshot.CreatedAt) && s.snapshot.RevokedAt.IsZero() && now.Before(s.snapshot.AccessExpiresAt) && now.Before(s.snapshot.ExpiresAt)
}
func (s Session) Renewable(now time.Time) bool {
	return !now.Before(s.snapshot.CreatedAt) && s.snapshot.RevokedAt.IsZero() && now.Before(s.snapshot.ExpiresAt)
}
func (s Session) Rotate(access, refresh string, now time.Time) (Session, error) {
	if !s.Renewable(now) {
		return Session{}, ErrCredential
	}
	if Time(now).Before(s.snapshot.UpdatedAt) {
		return Session{}, ErrState
	}
	if access == s.snapshot.AccessDigest || refresh == s.snapshot.RefreshDigest {
		return Session{}, ErrState
	}
	v := s.snapshot
	v.Generation++
	v.AccessDigest, v.RefreshDigest = access, refresh
	v.UpdatedAt = Time(now)
	v.AccessExpiresAt = v.UpdatedAt.Add(AccessLifetime)
	if v.AccessExpiresAt.After(v.ExpiresAt) {
		v.AccessExpiresAt = v.ExpiresAt
	}
	return RestoreSession(v)
}
func (s Session) Revoke(now time.Time) (Session, error) {
	v := s.snapshot
	v.Generation++
	v.UpdatedAt = Time(now)
	v.RevokedAt = v.UpdatedAt
	// Revocation may occur after the access grant expired. Its original expiry stays evidence.
	if !v.ID.Valid() || !canonical(v.UpdatedAt) || v.UpdatedAt.Before(s.snapshot.UpdatedAt) || v.Generation > 1<<53-1 {
		return Session{}, ErrState
	}
	return RestoreSession(v)
}
func (Session) String() string   { return "identity.Session{redacted}" }
func (Session) GoString() string { return "identity.Session{redacted}" }

type Fact struct {
	PrincipalID, SessionID id.ID
	Action, Reason         string
	Version                uint64
	OccurredAt             time.Time
}

func (p Principal) Fact() Fact {
	return Fact{PrincipalID: p.snapshot.ID, Action: "principal.registered", Version: 1, OccurredAt: p.snapshot.CreatedAt}
}
func (s Session) Fact(action, reason string) Fact {
	return Fact{PrincipalID: s.snapshot.PrincipalID, SessionID: s.snapshot.ID, Action: action, Reason: reason, Version: s.snapshot.Generation, OccurredAt: s.snapshot.UpdatedAt}
}
