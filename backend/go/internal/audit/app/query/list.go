package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type List struct {
	Store  app.Store
	Access app.AccountAccess
}
type Input struct {
	Principal principal.Principal
	AccountID id.ID
	After     uint64
	Limit     int
}

func (h List) Handle(ctx context.Context, input Input) (app.Page, error) {
	if err := ctx.Err(); err != nil {
		return app.Page{}, err
	}
	if !input.Principal.ID.Valid() {
		return app.Page{}, principal.ErrRequired
	}
	if !input.AccountID.Valid() {
		return app.Page{}, id.ErrInvalid
	}
	if input.Limit < 1 || input.Limit > 100 || input.After > 1<<53-1 {
		return app.Page{}, app.ErrInput
	}
	allowed, err := h.Access.Allowed(ctx, input.Principal.ID, input.AccountID)
	if err != nil {
		return app.Page{}, err
	}
	if !allowed {
		return app.Page{}, app.ErrDenied
	}
	return h.Store.List(ctx, input.AccountID, input.After, input.Limit)
}
