package app

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

var ErrChallenge = apperrors.New(apperrors.Invalid, "identity.challenge_refused", "challenge invalid, consumed or expired").WithPublic("Request a new verification or recovery code.")
var ErrContact = apperrors.New(apperrors.Conflict, "identity.contact_conflict", "verified address cannot be assigned").WithPublic("That recovery address cannot be assigned.")
var ErrMail = apperrors.New(apperrors.Unavailable, "identity.mail_unavailable", "identity mail could not be accepted").WithPublic("Email is unavailable. Try again later.")

// RecoveryStore operations compare the password checkpoint atomically. Password
// mutation consumes all challenges, revokes all sessions and appends required facts.
type RecoveryStore interface {
	Contact(context.Context, id.ID) (domain.Contact, bool, error)
	ByEmail(context.Context, string) (domain.Principal, bool, error)
	BeginChallenge(context.Context, domain.Challenge) error
	VerifyEmail(context.Context, string, time.Time, events.Metadata) error
	ResetPassword(context.Context, string, string, time.Time, events.Metadata) error
	ChangePassword(context.Context, id.ID, string, string, time.Time, events.Metadata) error
	IssueChecked(context.Context, domain.Session, string, events.Metadata) error
	PruneChallenges(context.Context, time.Time, int) (int, error)
}
type Mail struct{ To, Purpose, Token string }

func (Mail) String() string   { return "identity.Mail{redacted}" }
func (Mail) GoString() string { return "identity.Mail{redacted}" }

type Mailer interface {
	Send(context.Context, Mail) error
}
