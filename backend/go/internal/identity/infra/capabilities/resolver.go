package capabilities

import (
	"context"
	"time"

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
		PrincipalID      id.ID     `json:"principal_id"`
		SessionID        id.ID     `json:"session_id"`
		SessionExpiresAt time.Time `json:"session_expires_at"`
	}
	err := r.Invoker.Invoke(ctx, "identity.credential.resolve.v2", struct {
		AccessToken string `json:"access_token"`
	}{token}, &answer)
	if err != nil {
		return principal.Principal{}, err
	}
	if !answer.PrincipalID.Valid() || !answer.SessionID.Valid() || answer.SessionExpiresAt.IsZero() || answer.SessionExpiresAt.Year() < 1970 || answer.SessionExpiresAt.Year() > 9999 {
		return principal.Principal{}, capability.ErrResponse
	}
	return principal.Principal{ID: answer.PrincipalID, SessionID: answer.SessionID, SessionExpiresAt: answer.SessionExpiresAt}, nil
}
