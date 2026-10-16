package command

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Create struct {
	Store app.Store
	Clock clock.Clock
	IDs   id.Generator
}
type CreateInput struct {
	Principal principal.Principal
	Name      string
	Metadata  events.Metadata
}

func (h Create) Handle(ctx context.Context, input CreateInput) (domain.Account, error) {
	if err := ctx.Err(); err != nil {
		return domain.Account{}, err
	}
	if !input.Principal.ID.Valid() {
		return domain.Account{}, principal.ErrRequired
	}
	name, err := domain.Name(input.Name)
	if err != nil {
		return domain.Account{}, err
	}
	identity, err := h.IDs.NewID()
	if err != nil {
		return domain.Account{}, apperrors.New(apperrors.Internal, "account.id_failed", "account ID generation failed").WithCause(err)
	}
	account, err := domain.New(identity, input.Principal.ID, name, h.Clock.Now())
	if err != nil {
		return domain.Account{}, err
	}
	fact, err := account.Fact(domain.Created, input.Principal.ID)
	if err != nil {
		return domain.Account{}, err
	}
	if err := h.Store.Create(ctx, account, fact, input.Metadata); err != nil {
		return domain.Account{}, err
	}
	return account, nil
}
