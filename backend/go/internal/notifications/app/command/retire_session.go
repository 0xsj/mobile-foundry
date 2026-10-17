package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
)

type RetireSession struct{ Store app.Installations }

func (h RetireSession) Handle(ctx context.Context, input app.SessionRevocation) error {
	if err := input.Validate(); err != nil {
		return err
	}
	return h.Store.RetireSession(ctx, input)
}
