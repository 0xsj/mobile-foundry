package app

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

// Store atomically commits a successful state change and immutable receipt.
// Replay is checked before current-row/version checks, within the same transaction.
type Store interface {
	Apply(context.Context, id.ID, id.ID, domain.Write, id.ID) (domain.Receipt, error)
	Receipt(context.Context, id.ID, id.ID, id.ID) (domain.Receipt, bool, error)
	Get(context.Context, id.ID, id.ID) (domain.Note, bool, error)
	List(context.Context, id.ID, id.ID, int) ([]domain.Note, error)
}

// AccountAccess is owned by this consumer. Providers never expose Account stores.
type AccountAccess interface {
	Allowed(context.Context, id.ID, id.ID) (bool, error)
}

var ErrForbidden = apperrors.New(apperrors.Forbidden, "notes.account_forbidden", "Notes account access denied").WithPublic("You cannot access notes in this account.")

func Authorize(ctx context.Context, access AccountAccess, actor, account id.ID) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if !actor.Valid() || !account.Valid() {
		return id.ErrInvalid
	}
	allowed, err := access.Allowed(ctx, actor, account)
	if err != nil {
		return err
	}
	if !allowed {
		return ErrForbidden
	}
	return nil
}

// Changes emits optional, lossy invalidation only after a confirmed store result.
type Changes interface{ Changed(id.ID) }
