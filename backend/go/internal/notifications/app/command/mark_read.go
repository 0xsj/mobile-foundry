package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type MarkReadInput struct {
	Scope  app.Scope
	ItemID id.ID
}
type MarkRead struct {
	Store  app.Store
	Access app.AccountAccess
	Clock  clock.Clock
}

func (h MarkRead) Handle(ctx context.Context, input MarkReadInput) (domain.Item, error) {
	if err := app.Authorize(ctx, h.Access, input.Scope); err != nil {
		return domain.Item{}, err
	}
	if !input.ItemID.Valid() {
		return domain.Item{}, app.ErrInput
	}
	return h.Store.MarkRead(ctx, input.Scope, input.ItemID, h.Clock.Now())
}
