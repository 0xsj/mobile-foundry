package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
)

type GetPreferences struct {
	Store  app.Store
	Access app.AccountAccess
}

func (h GetPreferences) Handle(ctx context.Context, scope app.Scope) (bool, error) {
	if err := app.Authorize(ctx, h.Access, scope); err != nil {
		return false, err
	}
	return h.Store.MediaEnabled(ctx, scope)
}
