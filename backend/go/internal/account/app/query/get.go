package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Get struct{ Store app.Store }

func (h Get) Handle(ctx context.Context, actor principal.Principal, accountID id.ID) (domain.Account, error) {
	if err := ctx.Err(); err != nil {
		return domain.Account{}, err
	}
	if !actor.ID.Valid() {
		return domain.Account{}, principal.ErrRequired
	}
	if !accountID.Valid() {
		return domain.Account{}, id.ErrInvalid
	}
	account, found, err := h.Store.Get(ctx, accountID)
	if err != nil {
		return domain.Account{}, err
	}
	if !found {
		return domain.Account{}, app.ErrNotFound
	}
	if !account.OwnedBy(actor.ID) {
		return domain.Account{}, app.ErrDenied
	}
	return account, nil
}
