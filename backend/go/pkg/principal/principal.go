// Package principal defines identity admitted by a credential adapter, not authentication itself.
package principal

import (
	"context"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type Principal struct{ ID id.ID }

// Resolver verifies a credential before returning a trusted principal. It must
// not infer identity from a caller-provided principal/account ID.
type Resolver interface {
	Resolve(context.Context, string) (Principal, error)
}

var ErrRequired = apperrors.New(apperrors.Unauthenticated, "principal.authentication_required", "no accepted credential").WithPublic("Sign in to continue.")

type Deny struct{}

func (Deny) Resolve(ctx context.Context, _ string) (Principal, error) {
	if err := ctx.Err(); err != nil {
		return Principal{}, err
	}
	return Principal{}, ErrRequired
}
