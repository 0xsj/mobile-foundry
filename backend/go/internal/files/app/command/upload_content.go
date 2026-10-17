package command

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type UploadContent struct {
	Store   app.Store
	Objects app.Objects
	Access  app.AccountAccess
}
type UploadContentInput struct {
	Scope       app.Scope
	FileID      id.ID
	ContentType string
	Bytes       []byte
}

func (h UploadContent) Handle(ctx context.Context, in UploadContentInput) error {
	if err := app.Authorize(ctx, h.Access, in.Scope); err != nil {
		return err
	}
	if !in.FileID.Valid() {
		return app.ErrInput
	}
	f, found, err := h.Store.Get(ctx, in.Scope.Account, in.FileID)
	if err != nil {
		return err
	}
	if !found || f.Snapshot().Status == domain.Deleted {
		return app.ErrMissing
	}
	s := f.Snapshot()
	if in.ContentType != s.Spec.ContentType {
		return app.ErrContent
	}
	if err := h.Objects.Put(ctx, s, in.Bytes); err != nil {
		return err
	}
	// A delete racing this external write must not reopen a tombstone. Best-effort
	// cleanup here covers the normal race; a crash can leave inaccessible orphan bytes.
	current, found, err := h.Store.Get(ctx, in.Scope.Account, in.FileID)
	if err != nil {
		return err
	}
	if !found || current.Snapshot().Status == domain.Deleted {
		if err := h.Objects.Delete(ctx, s); err != nil {
			return err
		}
		return app.ErrMissing
	}
	return nil
}
