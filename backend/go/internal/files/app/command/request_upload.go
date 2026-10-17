package command

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type RequestUpload struct {
	Store  app.Store
	Access app.AccountAccess
	IDs    id.Generator
	Clock  clock.Clock
}
type RequestUploadInput struct {
	Scope    app.Scope
	Spec     domain.Spec
	Key      string
	Metadata events.Metadata
}

func (h RequestUpload) Handle(ctx context.Context, in RequestUploadInput) (app.Receipt, error) {
	if err := app.Authorize(ctx, h.Access, in.Scope); err != nil {
		return app.Receipt{}, err
	}
	spec, err := domain.Normalize(in.Spec)
	if err != nil {
		return app.Receipt{}, err
	}
	c := app.Command{Action: app.Request, Key: in.Key, Spec: spec}
	if err := c.Validate(); err != nil {
		return app.Receipt{}, err
	}
	if r, found, err := h.Store.Replay(ctx, in.Scope, c); err != nil || found {
		return r, err
	}
	candidate, err := h.IDs.NewID()
	if err != nil {
		return app.Receipt{}, err
	}
	return h.Store.Apply(ctx, in.Scope, c, candidate, h.Clock.Now(), in.Metadata)
}
