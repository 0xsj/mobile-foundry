package query

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type DownloadFile struct {
	Store   app.Store
	Objects app.Objects
	Access  app.AccountAccess
}
type DownloadFileInput struct {
	Scope  app.Scope
	FileID id.ID
}
type Content struct {
	File  domain.Snapshot
	Bytes []byte
}

func (h DownloadFile) Handle(ctx context.Context, in DownloadFileInput) (Content, error) {
	s, err := (GetFile{h.Store, h.Access}).Handle(ctx, GetFileInput{in.Scope, in.FileID})
	if err != nil {
		return Content{}, err
	}
	if s.Status != domain.Available {
		return Content{}, domain.ErrTransition
	}
	data, err := h.Objects.Get(ctx, s)
	if err != nil {
		return Content{}, err
	}
	current, found, err := h.Store.Get(ctx, in.Scope.Account, in.FileID)
	if err != nil {
		return Content{}, err
	}
	if !found || current.Snapshot().Status != domain.Available {
		return Content{}, app.ErrMissing
	}
	return Content{s, data}, nil
}
