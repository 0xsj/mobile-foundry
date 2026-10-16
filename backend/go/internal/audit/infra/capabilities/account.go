package capabilities

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const AccountAccess = "account.access.v1"

type Client struct{ Invoker capability.Invoker }

func (c Client) Allowed(ctx context.Context, principalID, accountID id.ID) (bool, error) {
	request := struct {
		PrincipalID id.ID  `json:"principal_id"`
		AccountID   id.ID  `json:"account_id"`
		Action      string `json:"action"`
	}{principalID, accountID, "account.read"}
	var response struct {
		Allowed *bool `json:"allowed"`
	}
	if err := c.Invoker.Invoke(ctx, AccountAccess, request, &response); err != nil {
		return false, err
	}
	if response.Allowed == nil {
		return false, capability.ErrResponse
	}
	return *response.Allowed, nil
}
