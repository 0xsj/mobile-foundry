package domain

import (
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"regexp"
	"time"
)

var ErrInstallationSpec = apperrors.New(apperrors.Invalid, "notifications.invalid_installation", "invalid push route or opaque token").WithPublic("Check the provider, application, environment and token.")
var appName = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$`)
var digest = regexp.MustCompile(`^[0-9a-f]{64}$`)

type PushRoute struct{ Provider, Application, Environment string }

func (r PushRoute) Validate() error {
	if (r.Provider != "apns" && r.Provider != "fcm") || !appName.MatchString(r.Application) || (r.Environment != "sandbox" && r.Environment != "production") || r.Provider == "fcm" && r.Environment != "production" {
		return ErrInstallationSpec
	}
	return nil
}

// Token encoding belongs to a future provider adapter. Registry admission only
// bounds opaque printable ASCII; it does not attest that a provider issued it.
type InstallationSpec struct {
	Route PushRoute
	Token string
}

func (s InstallationSpec) Validate() error {
	if err := s.Route.Validate(); err != nil {
		return err
	}
	if len(s.Token) < 1 || len(s.Token) > 4096 {
		return ErrInstallationSpec
	}
	for _, v := range []byte(s.Token) {
		if v < 33 || v > 126 {
			return ErrInstallationSpec
		}
	}
	return nil
}
func (InstallationSpec) String() string   { return "notifications.InstallationSpec{redacted}" }
func (InstallationSpec) GoString() string { return "notifications.InstallationSpec{redacted}" }

type InstallationSnapshot struct {
	ID, PrincipalID, SessionID, RegistrationID id.ID
	ProofDigest                                string
	Spec                                       InstallationSpec
	Version                                    uint64
	Enabled                                    bool
	CreatedAt, UpdatedAt, ExpiresAt            time.Time
}

func (InstallationSnapshot) String() string   { return "notifications.InstallationSnapshot{redacted}" }
func (InstallationSnapshot) GoString() string { return "notifications.InstallationSnapshot{redacted}" }

type Installation struct{ state InstallationSnapshot }

func (Installation) String() string                   { return "notifications.Installation{redacted}" }
func (Installation) GoString() string                 { return "notifications.Installation{redacted}" }
func (i Installation) Snapshot() InstallationSnapshot { return i.state }
func RestoreInstallation(s InstallationSnapshot) (Installation, error) {
	if !s.ID.Valid() || !s.PrincipalID.Valid() || !s.SessionID.Valid() || !s.RegistrationID.Valid() || !digest.MatchString(s.ProofDigest) || s.Version < 1 || s.Version > 1<<53-1 || s.CreatedAt.IsZero() || s.UpdatedAt.Before(s.CreatedAt) || s.ExpiresAt.IsZero() || !s.ExpiresAt.After(s.CreatedAt) {
		return Installation{}, ErrState
	}
	if s.Enabled {
		if err := s.Spec.Validate(); err != nil {
			return Installation{}, ErrState
		}
	} else if s.Spec.Token != "" || s.Spec.Route.Validate() != nil {
		return Installation{}, ErrState
	}
	for _, v := range []time.Time{s.CreatedAt, s.UpdatedAt, s.ExpiresAt} {
		if v.Year() < 1970 || v.Year() > 9999 || !v.Equal(v.UTC().Truncate(time.Microsecond)) {
			return Installation{}, ErrState
		}
	}
	return Installation{state: s}, nil
}
func NewInstallation(identity, principal, session, registration id.ID, proof string, spec InstallationSpec, now, expires time.Time) (Installation, error) {
	return RestoreInstallation(InstallationSnapshot{ID: identity, PrincipalID: principal, SessionID: session, RegistrationID: registration, ProofDigest: proof, Spec: spec, Version: 1, Enabled: true, CreatedAt: now.UTC().Truncate(time.Microsecond), UpdatedAt: now.UTC().Truncate(time.Microsecond), ExpiresAt: expires.UTC().Truncate(time.Microsecond)})
}
func (i Installation) Bind(principal, session, registration id.ID, spec InstallationSpec, now, expires time.Time) (Installation, error) {
	s := i.state
	s.PrincipalID = principal
	s.SessionID = session
	s.RegistrationID = registration
	s.Spec = spec
	s.Enabled = true
	s.Version++
	s.UpdatedAt = now.UTC().Truncate(time.Microsecond)
	if s.UpdatedAt.Before(s.CreatedAt) || s.UpdatedAt.Before(i.state.UpdatedAt) {
		return Installation{}, ErrState
	}
	s.ExpiresAt = expires.UTC().Truncate(time.Microsecond)
	return RestoreInstallation(s)
}
func (i Installation) Disable(at time.Time) (Installation, error) {
	if !i.state.Enabled {
		return i, nil
	}
	s := i.state
	s.Enabled = false
	s.Spec.Token = ""
	s.Version++
	s.UpdatedAt = at.UTC().Truncate(time.Microsecond)
	if s.UpdatedAt.Before(i.state.UpdatedAt) {
		s.UpdatedAt = i.state.UpdatedAt
	}
	return RestoreInstallation(s)
}
