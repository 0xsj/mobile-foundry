package command

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Bootstrap struct {
	Directory app.Directory
	Clock     clock.Clock
	IDs       id.Generator
}

// Name is only used by the winning creation. Repeat bootstrap never renames.
func (h Bootstrap) Handle(ctx context.Context, input CreateInput) (app.BootstrapResult, error) {
	if err := ctx.Err(); err != nil {
		return app.BootstrapResult{}, err
	}
	if !input.Principal.ID.Valid() {
		return app.BootstrapResult{}, principal.ErrRequired
	}
	name, err := domain.Name(input.Name)
	if err != nil {
		return app.BootstrapResult{}, err
	}
	identity, err := h.IDs.NewID()
	if err != nil {
		return app.BootstrapResult{}, apperrors.New(apperrors.Internal, "account.id_failed", "account ID generation failed").WithCause(err)
	}
	candidate, err := domain.New(identity, input.Principal.ID, name, h.Clock.Now())
	if err != nil {
		return app.BootstrapResult{}, err
	}
	fact, err := candidate.Fact(domain.Created, input.Principal.ID)
	if err != nil {
		return app.BootstrapResult{}, err
	}
	account, created, err := h.Directory.Bootstrap(ctx, candidate, fact, input.Metadata)
	if err != nil {
		return app.BootstrapResult{}, err
	}
	snapshot := account.Snapshot()
	if _, err := domain.Restore(snapshot); err != nil || snapshot.OwnerID != input.Principal.ID || (created && snapshot != candidate.Snapshot()) {
		return app.BootstrapResult{}, domain.ErrState
	}
	return app.BootstrapResult{Account: account, Created: created}, nil
}
