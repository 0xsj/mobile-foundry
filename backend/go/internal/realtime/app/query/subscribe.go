package query

import (
	"context"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

var ErrForbidden = apperrors.New(apperrors.Forbidden, "realtime.account_forbidden", "account access refused")

type Access interface {
	Allowed(context.Context, id.ID, id.ID) (bool, error)
}
type Feed interface {
	Watch(id.ID, id.ID) (<-chan struct{}, func(), error)
}
type Subscribe struct {
	Access Access
	Feed   Feed
}

func (h Subscribe) Check(ctx context.Context, actor, account id.ID) error {
	if !actor.Valid() || !account.Valid() {
		return id.ErrInvalid
	}
	allowed, err := h.Access.Allowed(ctx, actor, account)
	if err != nil {
		return err
	}
	if !allowed {
		return ErrForbidden
	}
	return nil
}
func (h Subscribe) Handle(ctx context.Context, actor, account id.ID) (<-chan struct{}, func(), error) {
	if err := h.Check(ctx, actor, account); err != nil {
		return nil, nil, err
	}
	return h.Feed.Watch(actor, account)
}
