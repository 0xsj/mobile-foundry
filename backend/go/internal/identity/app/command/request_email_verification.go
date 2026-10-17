package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

type RequestEmailVerification struct {
	Store     app.Store
	Recovery  app.RecoveryStore
	Passwords app.Passwords
	Tokens    app.Tokens
	Mail      app.Mailer
	Clock     clock.Clock
}
type RequestEmailVerificationInput struct {
	PrincipalID     id.ID
	Email, Password string
}

func (RequestEmailVerificationInput) String() string {
	return "identity.RequestEmailVerificationInput{redacted}"
}
func (h RequestEmailVerification) Handle(ctx context.Context, in RequestEmailVerificationInput) error {
	address, err := domain.Email(in.Email)
	if err != nil {
		return err
	}
	if err = domain.Password(in.Password); err != nil {
		return domain.ErrCredential
	}
	p, found, err := h.Store.ByID(ctx, in.PrincipalID)
	if err != nil {
		return err
	}
	if !found {
		return domain.ErrCredential
	}
	matched, err := h.Passwords.Verify(ctx, p.Snapshot().PasswordHash, in.Password)
	if err != nil {
		return err
	}
	if !matched {
		return domain.ErrCredential
	}
	token, digest, err := h.Tokens.Issue(ctx, domain.VerifyEmail)
	if err != nil {
		return err
	}
	now := domain.Time(h.Clock.Now())
	challenge := domain.Challenge{PrincipalID: in.PrincipalID, Purpose: domain.VerifyEmail, Email: address, Digest: digest, CredentialHash: p.Snapshot().PasswordHash, CreatedAt: now, ExpiresAt: now.Add(20 * time.Minute)}
	if err = h.Recovery.BeginChallenge(ctx, challenge); err != nil {
		return err
	}
	return h.Mail.Send(ctx, app.Mail{To: address, Purpose: domain.VerifyEmail, Token: token})
}

func (v RequestEmailVerificationInput) GoString() string { return v.String() }
