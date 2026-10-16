package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const Read = "account.read"
const Rename = "account.rename"

type Access struct{ Store app.Store }

func (h Access) Handle(ctx context.Context, principalID, accountID id.ID, action string) (bool, error) {
	if err := ctx.Err(); err != nil {
		return false, err
	}
	if !principalID.Valid() || !accountID.Valid() {
		return false, id.ErrInvalid
	}
	if action != Read && action != Rename {
		return false, apperrors.New(apperrors.Invalid, "account.invalid_action", "unsupported account action").WithPublic("Use a supported account action.")
	}
	account, found, err := h.Store.Get(ctx, accountID)
	if err != nil {
		return false, err
	}
	return found && account.OwnedBy(principalID), nil
}
