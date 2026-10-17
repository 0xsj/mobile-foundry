package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
)

type DeliverUpload struct {
	Store app.Store
	Clock clock.Clock
}

func (h DeliverUpload) Handle(ctx context.Context, input app.Delivery) error {
	if err := input.Validate(); err != nil {
		return err
	}
	return h.Store.Deliver(ctx, input, h.Clock.Now())
}
