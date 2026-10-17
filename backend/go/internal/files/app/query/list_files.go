package query

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type ListFiles struct {
	Store  app.Store
	Access app.AccountAccess
}
type ListFilesInput struct {
	Scope app.Scope
	After id.ID
	Limit int
}
type Page struct {
	Files []domain.Snapshot
	Next  id.ID
}

func (h ListFiles) Handle(ctx context.Context, in ListFilesInput) (Page, error) {
	if err := app.Authorize(ctx, h.Access, in.Scope); err != nil {
		return Page{}, err
	}
	if in.Limit < 1 || in.Limit > 100 || in.After != "" && !in.After.Valid() {
		return Page{}, app.ErrInput
	}
	rows, err := h.Store.List(ctx, in.Scope.Account, in.After, in.Limit+1)
	if err != nil {
		return Page{}, err
	}
	if len(rows) > in.Limit+1 {
		return Page{}, domain.ErrState
	}
	p := Page{Files: []domain.Snapshot{}}
	prior := in.After
	for _, f := range rows {
		s := f.Snapshot()
		if s.AccountID != in.Scope.Account || s.ID <= prior || s.Status == domain.Deleted {
			return Page{}, domain.ErrState
		}
		prior = s.ID
		p.Files = append(p.Files, s)
	}
	if len(p.Files) > in.Limit {
		p.Files = p.Files[:in.Limit]
		p.Next = p.Files[in.Limit-1].ID
	}
	return p, nil
}
