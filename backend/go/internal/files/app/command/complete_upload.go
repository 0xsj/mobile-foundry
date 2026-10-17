package command

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type CompleteUpload struct {
	Store   app.Store
	Objects app.Objects
	Access  app.AccountAccess
	Clock   clock.Clock
}
type CompleteUploadInput struct {
	Scope    app.Scope
	FileID   id.ID
	Expected uint64
	Key      string
	Metadata events.Metadata
}

func (h CompleteUpload) Handle(ctx context.Context, in CompleteUploadInput) (app.Receipt, error) {
	if err := app.Authorize(ctx, h.Access, in.Scope); err != nil {
		return app.Receipt{}, err
	}
	c := app.Command{Action: app.Complete, Key: in.Key, FileID: in.FileID, Expected: in.Expected}
	if err := c.Validate(); err != nil {
		return app.Receipt{}, err
	}
	if r, found, err := h.Store.Replay(ctx, in.Scope, c); err != nil || found {
		return r, err
	}
	f, found, err := h.Store.Get(ctx, in.Scope.Account, in.FileID)
	if err != nil {
		return app.Receipt{}, err
	}
	if !found || f.Snapshot().Status == domain.Deleted {
		return app.Receipt{}, app.ErrMissing
	}
	if _, err := h.Objects.Get(ctx, f.Snapshot()); err != nil {
		return app.Receipt{}, err
	}
	return h.Store.Apply(ctx, in.Scope, c, "", h.Clock.Now(), in.Metadata)
}
