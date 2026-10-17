package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
)

type VerifyEmail struct {
	Store  app.RecoveryStore
	Tokens app.Tokens
	Clock  clock.Clock
}

func (h VerifyEmail) Handle(ctx context.Context, token string, m events.Metadata) error {
	digest, err := h.Tokens.Digest(ctx, token, domain.VerifyEmail)
	if err != nil {
		return app.ErrChallenge
	}
	return h.Store.VerifyEmail(ctx, digest, h.Clock.Now(), m)
}
