package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type Read struct {
	Store  app.Store
	Access app.AccountAccess
}

var ErrReceipt = apperrors.New(apperrors.NotFound, "notes.receipt_not_found", "no committed receipt observed").WithPublic("No committed receipt was found. Keep the original command before retrying.")

func (h Read) Receipt(ctx context.Context, actor, account, command id.ID) (domain.Receipt, error) {
	if err := app.Authorize(ctx, h.Access, actor, account); err != nil {
		return domain.Receipt{}, err
	}
	if !command.Valid() {
		return domain.Receipt{}, id.ErrInvalid
	}
	r, found, err := h.Store.Receipt(ctx, account, actor, command)
	if err != nil {
		return r, err
	}
	if !found {
		return r, ErrReceipt
	}
	if !r.Valid() || r.AccountID != account || r.PrincipalID != actor || r.CommandID != command {
		return domain.Receipt{}, domain.ErrState
	}
	return r, nil
}
func (h Read) Get(ctx context.Context, actor, account, noteID id.ID) (domain.Note, error) {
	if err := app.Authorize(ctx, h.Access, actor, account); err != nil {
		return domain.Note{}, err
	}
	if !noteID.Valid() {
		return domain.Note{}, id.ErrInvalid
	}
	n, found, err := h.Store.Get(ctx, account, noteID)
	if err != nil {
		return n, err
	}
	if !found {
		return n, domain.ErrMissing
	}
	if !n.Valid() || n.ID != noteID {
		return domain.Note{}, domain.ErrState
	}
	return n, nil
}

type Page struct {
	Notes []domain.Note
	Next  id.ID
}

func (h Read) List(ctx context.Context, actor, account, after id.ID, limit int) (Page, error) {
	if err := app.Authorize(ctx, h.Access, actor, account); err != nil {
		return Page{}, err
	}
	if limit < 1 || limit > 100 || (after != "" && !after.Valid()) {
		return Page{}, domain.ErrWrite
	}
	notes, err := h.Store.List(ctx, account, after, limit+1)
	if err != nil {
		return Page{}, err
	}
	if len(notes) > limit+1 {
		return Page{}, domain.ErrState
	}
	previous := after
	for _, n := range notes {
		if !n.Valid() || n.ID <= previous {
			return Page{}, domain.ErrState
		}
		previous = n.ID
	}
	p := Page{Notes: notes}
	if len(notes) > limit {
		p.Notes = notes[:limit]
		p.Next = p.Notes[limit-1].ID
	}
	return p, nil
}
