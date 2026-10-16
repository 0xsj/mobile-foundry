package capabilities

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Resolver struct{ Invoker capability.Invoker }

func (r Resolver) Resolve(ctx context.Context, token string) (principal.Principal, error) {
	if err := ctx.Err(); err != nil {
		return principal.Principal{}, err
	}
	if len(token) != 46 {
		return principal.Principal{}, principal.ErrRequired
	}
	var answer struct {
		PrincipalID id.ID `json:"principal_id"`
	}
	err := r.Invoker.Invoke(ctx, "identity.credential.resolve.v1", struct {
		AccessToken string `json:"access_token"`
	}{token}, &answer)
	if err != nil {
		return principal.Principal{}, err
	}
	if !answer.PrincipalID.Valid() {
		return principal.Principal{}, capability.ErrResponse
	}
	return principal.Principal{ID: answer.PrincipalID}, nil
}
