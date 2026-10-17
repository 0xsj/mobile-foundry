package app

import (
	"context"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

var (
	ErrPushTarget   = apperrors.New(apperrors.Conflict, "notifications.push_target_changed", "push binding is absent, disabled, expired or changed")
	ErrPushSession  = apperrors.New(apperrors.Forbidden, "notifications.push_session_ineligible", "push session is not eligible")
	ErrPushResponse = apperrors.New(apperrors.Internal, "notifications.push_invalid_response", "push gateway returned an invalid disposition")
)

// PushLease pins a trusted internal intent to one installation revision/owner.
// It is not a public credential, installation proof or permission to switch owners.
type PushLease struct {
	InstallationID, PrincipalID, SessionID id.ID
	Version                                uint64
}

func (PushLease) String() string   { return "notifications.PushLease{redacted}" }
func (PushLease) GoString() string { return "notifications.PushLease{redacted}" }

func (l PushLease) Validate() error {
	if !l.InstallationID.Valid() || !l.PrincipalID.Valid() || !l.SessionID.Valid() || l.Version < 1 || l.Version > 1<<53-1 {
		return ErrInput
	}
	return nil
}

func (l PushLease) Matches(s domain.InstallationSnapshot) bool {
	return s.ID == l.InstallationID && s.PrincipalID == l.PrincipalID && s.SessionID == l.SessionID && s.Version == l.Version
}

type PushAddress struct {
	Lease        PushLease
	Spec         domain.InstallationSpec
	RegisteredAt time.Time // Last binding/token update, for timestamped provider rejection.
}

func (PushAddress) String() string   { return "notifications.PushAddress{redacted}" }
func (PushAddress) GoString() string { return "notifications.PushAddress{redacted}" }

// PushTargets never holds a transaction/lock across a foreign capability or send.
type PushTargets interface {
	PushTarget(context.Context, PushLease, time.Time) (PushAddress, error)
	// A stale rejection is a successful no-op. Never clear a newer binding/token.
	RetirePushTarget(context.Context, PushLease, time.Time) (bool, error)
}

// SessionEligibility is consumer-owned. An outage is an error, never eligible=false.
type SessionEligibility interface {
	SessionEligible(context.Context, id.ID, id.ID) (bool, error)
}

type PushDisposition string

const (
	PushAccepted      PushDisposition = "accepted"
	PushInvalidTarget PushDisposition = "invalid_target"
)

// A gateway must allowlist routes/credentials, validate provider encoding, honor
// context and attempt once. Only definitive invalid-address evidence returns
// PushInvalidTarget; timeouts, throttling, auth/config and uncertain acks are errors.
// PreviewID is the only payload; no account, inbox content or caller-supplied text.
type PushGateway interface {
	SendPreview(ctx context.Context, address PushAddress, previewID id.ID) (PushDisposition, error)
}

type PushAttempt struct {
	Disposition PushDisposition
	Retired     bool
}
