package v1

import (
	"context"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const Name = "account.access.v1"

type Access interface {
	Handle(context.Context, id.ID, id.ID, string) (bool, error)
}

func Provide(access Access) capability.Provision {
	return capability.Provision{Name: Name, Callers: []string{"audit", "notes"}, Handler: func(ctx context.Context, wire json.RawMessage) (any, error) {
		var input struct {
			PrincipalID string `json:"principal_id"`
			AccountID   string `json:"account_id"`
			Action      string `json:"action"`
		}
		if err := capability.Decode(wire, &input); err != nil {
			return nil, err
		}
		principalID, err := id.Parse(input.PrincipalID)
		if err != nil {
			return nil, err
		}
		accountID, err := id.Parse(input.AccountID)
		if err != nil {
			return nil, err
		}
		if input.Action != query.Read && input.Action != query.Rename {
			return nil, capability.ErrRequest
		}
		allowed, err := access.Handle(ctx, principalID, accountID, input.Action)
		if err != nil {
			return nil, err
		}
		return struct {
			Allowed bool `json:"allowed"`
		}{allowed}, nil
	}}
}
