package command

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Rename struct {
	Store app.Store
	Clock clock.Clock
}
type RenameInput struct {
	Principal       principal.Principal
	AccountID       id.ID
	Name            string
	ExpectedVersion uint64
	Metadata        events.Metadata
}

func (h Rename) Handle(ctx context.Context, input RenameInput) (domain.Account, error) {
	if err := ctx.Err(); err != nil {
		return domain.Account{}, err
	}
	if !input.Principal.ID.Valid() {
		return domain.Account{}, principal.ErrRequired
	}
	if !input.AccountID.Valid() {
		return domain.Account{}, id.ErrInvalid
	}
	account, found, err := h.Store.Get(ctx, input.AccountID)
	if err != nil {
		return domain.Account{}, err
	}
	if !found {
		return domain.Account{}, app.ErrNotFound
	}
	if !account.OwnedBy(input.Principal.ID) {
		return domain.Account{}, app.ErrDenied
	}
	if input.ExpectedVersion == 0 || account.Snapshot().Version != input.ExpectedVersion {
		return domain.Account{}, app.ErrConflict
	}
	next, err := account.Rename(input.Name, h.Clock.Now())
	if err != nil {
		return domain.Account{}, err
	}
	fact, err := next.Fact(domain.Renamed, input.Principal.ID)
	if err != nil {
		return domain.Account{}, err
	}
	if err := h.Store.Rename(ctx, next, input.ExpectedVersion, fact, input.Metadata); err != nil {
		return domain.Account{}, err
	}
	return next, nil
}
