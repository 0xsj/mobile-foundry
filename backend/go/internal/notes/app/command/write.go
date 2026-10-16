package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type Write struct {
	Store  app.Store
	Access app.AccountAccess
	IDs    id.Generator
}

func (h Write) Handle(ctx context.Context, actor, account id.ID, input domain.Write) (domain.Receipt, error) {
	if err := app.Authorize(ctx, h.Access, actor, account); err != nil {
		return domain.Receipt{}, err
	}
	if err := input.Validate(); err != nil {
		return domain.Receipt{}, err
	}
	var candidate id.ID
	if input.Action == domain.Create {
		// A known replay does not need fresh allocation. Apply still owns the
		// atomic fingerprint check, including races after this read.
		_, found, err := h.Store.Receipt(ctx, account, actor, input.CommandID)
		if err != nil {
			return domain.Receipt{}, err
		}
		if !found {
			candidate, err = h.IDs.NewID()
			if err != nil {
				return domain.Receipt{}, err
			}
		}
	}
	result, err := h.Store.Apply(ctx, account, actor, input, candidate)
	if err == nil && (!result.Valid() || result.CommandID != input.CommandID || result.AccountID != account || result.PrincipalID != actor || result.Action != input.Action || result.Title != input.Title || (input.Action != domain.Create && (result.NoteID != input.NoteID || result.Version != input.Expected+1))) {
		return domain.Receipt{}, domain.ErrState
	}
	return result, err
}
