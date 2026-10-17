package capabilities

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type Client struct{ Invoker capability.Invoker }

func (c Client) Allowed(ctx context.Context, actor, account id.ID) (bool, error) {
	request := struct {
		PrincipalID id.ID  `json:"principal_id"`
		AccountID   id.ID  `json:"account_id"`
		Action      string `json:"action"`
	}{actor, account, "account.read"}
	var response struct {
		Allowed *bool `json:"allowed"`
	}
	if err := c.Invoker.Invoke(ctx, "account.access.v1", request, &response); err != nil {
		return false, err
	}
	if response.Allowed == nil {
		return false, capability.ErrResponse
	}
	return *response.Allowed, nil
}
