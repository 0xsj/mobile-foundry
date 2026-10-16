package query

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Resolve struct {
	Store  app.Store
	Tokens app.Tokens
	Clock  clock.Clock
}

func (h Resolve) Handle(ctx context.Context, token string) (principal.Principal, error) {
	digest, err := h.Tokens.Digest(ctx, token, "access")
	if err != nil {
		return principal.Principal{}, err
	}
	identity, err := h.Store.Access(ctx, digest, h.Clock.Now())
	if err != nil {
		return principal.Principal{}, err
	}
	return principal.Principal{ID: identity}, nil
}

type Me struct{ Store app.Store }

func (h Me) Handle(ctx context.Context, identity id.ID) (domain.Principal, error) {
	p, found, err := h.Store.ByID(ctx, identity)
	if err != nil {
		return domain.Principal{}, err
	}
	if !found {
		return domain.Principal{}, domain.ErrCredential
	}
	return p, nil
}
