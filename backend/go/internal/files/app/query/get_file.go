package query

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type GetFile struct {
	Store  app.Store
	Access app.AccountAccess
}
type GetFileInput struct {
	Scope  app.Scope
	FileID id.ID
}

func (h GetFile) Handle(ctx context.Context, in GetFileInput) (domain.Snapshot, error) {
	if err := app.Authorize(ctx, h.Access, in.Scope); err != nil {
		return domain.Snapshot{}, err
	}
	if !in.FileID.Valid() {
		return domain.Snapshot{}, app.ErrInput
	}
	f, found, err := h.Store.Get(ctx, in.Scope.Account, in.FileID)
	if err != nil {
		return domain.Snapshot{}, err
	}
	if !found || f.Snapshot().Status == domain.Deleted {
		return domain.Snapshot{}, app.ErrMissing
	}
	return f.Snapshot(), nil
}
