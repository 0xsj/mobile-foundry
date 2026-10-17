package capabilities

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const SessionEligibilityCapability = "identity.session.eligible.v1"

func (c Client) SessionEligible(ctx context.Context, principal, session id.ID) (bool, error) {
	request := struct {
		PrincipalID id.ID `json:"principal_id"`
		SessionID   id.ID `json:"session_id"`
	}{principal, session}
	var response struct {
		Eligible *bool `json:"eligible"`
	}
	if err := c.Invoker.Invoke(ctx, SessionEligibilityCapability, request, &response); err != nil {
		return false, err
	}
	if response.Eligible == nil {
		return false, capability.ErrResponse
	}
	return *response.Eligible, nil
}
