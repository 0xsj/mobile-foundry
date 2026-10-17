package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
)

type ResetPassword struct {
	Store     app.RecoveryStore
	Tokens    app.Tokens
	Passwords app.Passwords
	Clock     clock.Clock
}
type ResetPasswordInput struct{ Token, Password string }

func (ResetPasswordInput) String() string { return "identity.ResetPasswordInput{redacted}" }
func (h ResetPassword) Handle(ctx context.Context, in ResetPasswordInput, m events.Metadata) error {
	if err := domain.Password(in.Password); err != nil {
		return err
	}
	digest, err := h.Tokens.Digest(ctx, in.Token, domain.ResetPassword)
	if err != nil {
		return app.ErrChallenge
	}
	hash, err := h.Passwords.Hash(ctx, in.Password)
	if err != nil {
		return err
	}
	return h.Store.ResetPassword(ctx, digest, hash, h.Clock.Now(), m)
}

func (v ResetPasswordInput) GoString() string { return v.String() }
