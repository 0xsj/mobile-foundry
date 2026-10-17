package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"time"
)

type SessionAccess interface {
	AccessSession(context.Context, string, time.Time) (principal.Principal, error)
}
type ResolveSession struct {
	Store  SessionAccess
	Tokens app.Tokens
	Clock  clock.Clock
}

func (h ResolveSession) Handle(ctx context.Context, token string) (principal.Principal, error) {
	digest, err := h.Tokens.Digest(ctx, token, "access")
	if err != nil {
		return principal.Principal{}, err
	}
	return h.Store.AccessSession(ctx, digest, h.Clock.Now())
}
