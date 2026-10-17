package v1

import (
	"context"
	"encoding/json"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const SessionEligibilityName = "identity.session.eligible.v1"

func ProvideSessionEligibility(h query.SessionEligibility) capability.Provision {
	return capability.Provision{Name: SessionEligibilityName, Callers: []string{"notifications"}, Handler: func(ctx context.Context, wire json.RawMessage) (any, error) {
		var body struct {
			PrincipalID id.ID `json:"principal_id"`
			SessionID   id.ID `json:"session_id"`
		}
		if err := capability.Decode(wire, &body); err != nil {
			return nil, err
		}
		eligible, err := h.Handle(ctx, body.PrincipalID, body.SessionID)
		if err != nil {
			return nil, err
		}
		return struct {
			Eligible bool `json:"eligible"`
		}{eligible}, nil
	}}
}
