package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"time"
)

type RequestPasswordReset struct {
	Recovery app.RecoveryStore
	Tokens   app.Tokens
	Mail     app.Mailer
	Clock    clock.Clock
}

func (h RequestPasswordReset) Handle(ctx context.Context, email string) error {
	address, err := domain.Email(email)
	if err != nil {
		return err
	}
	p, found, err := h.Recovery.ByEmail(ctx, address)
	if err != nil {
		return err
	}
	if !found {
		return nil
	}
	token, digest, err := h.Tokens.Issue(ctx, domain.ResetPassword)
	if err != nil {
		return err
	}
	now := domain.Time(h.Clock.Now())
	c := domain.Challenge{PrincipalID: p.Snapshot().ID, Purpose: domain.ResetPassword, Email: address, Digest: digest, CredentialHash: p.Snapshot().PasswordHash, CreatedAt: now, ExpiresAt: now.Add(20 * time.Minute)}
	if err = h.Recovery.BeginChallenge(ctx, c); err != nil {
		return err
	}
	return h.Mail.Send(ctx, app.Mail{To: address, Purpose: domain.ResetPassword, Token: token})
}
