package query

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type ListPrincipal struct{ Store app.PrincipalStore }

func (h ListPrincipal) Handle(ctx context.Context, actor principal.Principal, after uint64, limit int) (app.Page, error) {
	if err := ctx.Err(); err != nil {
		return app.Page{}, err
	}
	if !actor.ID.Valid() {
		return app.Page{}, principal.ErrRequired
	}
	if limit < 1 || limit > 100 || after > 1<<53-1 {
		return app.Page{}, app.ErrInput
	}
	return h.Store.ListPrincipal(ctx, actor.ID, after, limit)
}
