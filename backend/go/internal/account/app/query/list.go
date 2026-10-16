package query

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type List struct{ Directory app.Directory }
type ListInput struct {
	Principal principal.Principal
	After     id.ID
	Limit     int
}

func (h List) Handle(ctx context.Context, input ListInput) (app.Page, error) {
	if err := ctx.Err(); err != nil {
		return app.Page{}, err
	}
	if !input.Principal.ID.Valid() {
		return app.Page{}, principal.ErrRequired
	}
	if input.Limit < 1 || input.Limit > 100 || (input.After != "" && !input.After.Valid()) {
		return app.Page{}, app.ErrInput
	}
	rows, err := h.Directory.List(ctx, input.Principal.ID, input.After, input.Limit+1)
	if err != nil {
		return app.Page{}, err
	}
	if len(rows) > input.Limit+1 {
		return app.Page{}, domain.ErrState
	}
	prior := input.After
	for _, account := range rows {
		s := account.Snapshot()
		if _, err := domain.Restore(s); err != nil || s.OwnerID != input.Principal.ID || s.ID <= prior {
			return app.Page{}, domain.ErrState
		}
		prior = s.ID
	}
	page := app.Page{Accounts: rows}
	if len(rows) > input.Limit {
		page.Accounts = rows[:input.Limit]
		page.Next = page.Accounts[len(page.Accounts)-1].Snapshot().ID
	}
	return page, nil
}
