package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
)

type ListInbox struct {
	Store  app.Store
	Access app.AccountAccess
}

func (h ListInbox) Handle(ctx context.Context, input app.ListInput) (app.Page, error) {
	if err := app.Authorize(ctx, h.Access, input.Scope); err != nil {
		return app.Page{}, err
	}
	if err := input.Validate(); err != nil {
		return app.Page{}, err
	}
	return h.Store.List(ctx, input)
}
