package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
)

type SetMediaPreferenceInput struct {
	Scope   app.Scope
	Enabled bool
}
type SetMediaPreference struct {
	Store  app.Store
	Access app.AccountAccess
}

func (h SetMediaPreference) Handle(ctx context.Context, input SetMediaPreferenceInput) error {
	if err := app.Authorize(ctx, h.Access, input.Scope); err != nil {
		return err
	}
	return h.Store.SetMediaEnabled(ctx, input.Scope, input.Enabled)
}
