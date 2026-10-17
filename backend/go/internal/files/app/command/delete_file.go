package command

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type DeleteFile struct {
	Store   app.Store
	Objects app.Objects
	Access  app.AccountAccess
	Clock   clock.Clock
}
type DeleteFileInput struct {
	Scope    app.Scope
	FileID   id.ID
	Expected uint64
	Key      string
	Metadata events.Metadata
}

func (h DeleteFile) Handle(ctx context.Context, in DeleteFileInput) (app.Receipt, error) {
	if err := app.Authorize(ctx, h.Access, in.Scope); err != nil {
		return app.Receipt{}, err
	}
	c := app.Command{Action: app.Delete, Key: in.Key, FileID: in.FileID, Expected: in.Expected}
	if err := c.Validate(); err != nil {
		return app.Receipt{}, err
	}
	// Logical deletion commits first. A storage failure cannot leave an available
	// row pointing at removed bytes. Retrying the same key repeats physical cleanup.
	r, err := h.Store.Apply(ctx, in.Scope, c, "", h.Clock.Now(), in.Metadata)
	if err != nil {
		return app.Receipt{}, err
	}
	if err := h.Objects.Delete(ctx, r.File); err != nil {
		return app.Receipt{}, err
	}
	return r, nil
}
