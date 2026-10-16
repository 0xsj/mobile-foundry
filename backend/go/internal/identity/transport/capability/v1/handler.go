package v1

import (
	"context"
	"encoding/json"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const Name = "identity.credential.resolve.v1"

func Provide(query query.Resolve) capability.Provision {
	return capability.Provision{Name: Name, Callers: []string{"api"}, Handler: func(ctx context.Context, wire json.RawMessage) (any, error) {
		var body struct {
			AccessToken string `json:"access_token"`
		}
		if err := capability.Decode(wire, &body); err != nil {
			return nil, err
		}
		actor, err := query.Handle(ctx, body.AccessToken)
		if err != nil {
			return nil, err
		}
		return struct {
			PrincipalID id.ID `json:"principal_id"`
		}{actor.ID}, nil
	}}
}
