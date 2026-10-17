package app

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"time"
)

var (
	ErrInstallationMissing  = apperrors.New(apperrors.NotFound, "notifications.installation_not_found", "installation or proof not accepted").WithPublic("This installation is not available with that proof.")
	ErrInstallationConflict = apperrors.New(apperrors.Conflict, "notifications.installation_conflict", "installation checkpoint changed").WithPublic("Read the current installation before changing it. Use a new registration ID for a new registration.")
	ErrInstallationBinding  = apperrors.New(apperrors.Forbidden, "notifications.installation_binding", "installation is bound to another session").WithPublic("This session does not own the current binding.")
	ErrInstallationTarget   = apperrors.New(apperrors.Conflict, "notifications.installation_target", "push target already registered elsewhere").WithPublic("This target is already registered by another installation.")
	ErrInstallationSession  = apperrors.New(apperrors.Unauthenticated, "notifications.identity_session_required", "a current owned Identity session is required").WithPublic("Sign in with a current Identity session.")
)

type InstallationProofs interface {
	Digest(context.Context, string) (string, error)
}
type InstallationActor struct {
	Principal, Session id.ID
	ExpiresAt          time.Time
}

func InstallationActorFrom(p principal.Principal) InstallationActor {
	return InstallationActor{p.ID, p.SessionID, p.SessionExpiresAt}
}
func (a InstallationActor) Validate(now time.Time) error {
	if !a.Principal.Valid() || !a.Session.Valid() || a.ExpiresAt.IsZero() || !now.Before(a.ExpiresAt) || now.IsZero() || a.ExpiresAt.Year() > 9999 {
		return ErrInstallationSession
	}
	return nil
}

type RegisterInstallationInput struct {
	Actor              InstallationActor
	ID, RegistrationID id.ID
	Proof              string
	Spec               domain.InstallationSpec
	Expected           uint64 // zero creates; nonzero updates or explicitly rebinds
}

func (RegisterInstallationInput) String() string {
	return "notifications.RegisterInstallationInput{redacted}"
}
func (RegisterInstallationInput) GoString() string {
	return "notifications.RegisterInstallationInput{redacted}"
}

type InstallationMutation struct {
	Actor              InstallationActor
	ID, RegistrationID id.ID
	ProofDigest        string
	Spec               domain.InstallationSpec
	Expected           uint64
}

func (InstallationMutation) String() string   { return "notifications.InstallationMutation{redacted}" }
func (InstallationMutation) GoString() string { return "notifications.InstallationMutation{redacted}" }
func (m InstallationMutation) Validate(now time.Time) error {
	if err := m.Actor.Validate(now); err != nil {
		return err
	}
	if !m.ID.Valid() || !m.RegistrationID.Valid() || m.Expected > 1<<53-2 || len(m.ProofDigest) != 64 {
		return ErrInput
	}
	return m.Spec.Validate()
}

type SessionRevocation struct {
	EventID, PrincipalID, SessionID id.ID
	Version                         uint64
	Reason                          string
	OccurredAt                      time.Time
}

func (r SessionRevocation) Validate() error {
	if !r.EventID.Valid() || !r.PrincipalID.Valid() || !r.SessionID.Valid() || r.Version < 2 || r.Version > 1<<53-1 || (r.Reason != "sign_out" && r.Reason != "refresh_reuse") || r.OccurredAt.IsZero() || r.OccurredAt.Year() < 1970 || r.OccurredAt.Year() > 9999 {
		return ErrInput
	}
	return nil
}

type Installations interface {
	// Register guards proof, current version, session tombstones and unique target
	// atomically. Register never auto-replays an old version or registration ID.
	RegisterInstallation(context.Context, InstallationMutation, time.Time) (domain.Installation, error)
	GetInstallation(context.Context, InstallationActor, id.ID, string, time.Time) (domain.Installation, error)
	UnregisterInstallation(context.Context, InstallationActor, id.ID, string, uint64, time.Time) (domain.Installation, error)
	// RetireSession commits the tombstone, matching binding cleanup and inbox marker
	// together. An out-of-order late registration cannot resurrect that session.
	RetireSession(context.Context, SessionRevocation) error
}
