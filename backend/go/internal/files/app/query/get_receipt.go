package query

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
)

type GetReceipt struct {
	Store  app.Store
	Access app.AccountAccess
}
type GetReceiptInput struct {
	Scope app.Scope
	Key   string
}

func (h GetReceipt) Handle(ctx context.Context, in GetReceiptInput) (app.Receipt, error) {
	if err := app.Authorize(ctx, h.Access, in.Scope); err != nil {
		return app.Receipt{}, err
	}
	if !app.ValidKey(in.Key) {
		return app.Receipt{}, app.ErrInput
	}
	r, found, err := h.Store.Receipt(ctx, in.Scope, in.Key)
	if err != nil {
		return app.Receipt{}, err
	}
	if !found {
		return app.Receipt{}, app.ErrReceipt
	}
	return r, nil
}
