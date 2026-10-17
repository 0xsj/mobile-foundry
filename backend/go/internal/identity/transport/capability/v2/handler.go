package v2

import (
	"context"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

const Name = "identity.credential.resolve.v2"

func Provide(h query.ResolveSession) capability.Provision {
	return capability.Provision{Name: Name, Callers: []string{"api"}, Handler: func(ctx context.Context, wire json.RawMessage) (any, error) {
		var body struct {
			AccessToken string `json:"access_token"`
		}
		if err := capability.Decode(wire, &body); err != nil {
			return nil, err
		}
		actor, err := h.Handle(ctx, body.AccessToken)
		if err != nil {
			return nil, err
		}
		return struct {
			PrincipalID      id.ID     `json:"principal_id"`
			SessionID        id.ID     `json:"session_id"`
			SessionExpiresAt time.Time `json:"session_expires_at"`
		}{actor.ID, actor.SessionID, actor.SessionExpiresAt}, nil
	}}
}
