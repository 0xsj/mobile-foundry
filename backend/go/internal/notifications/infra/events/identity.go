package events

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const IdentityConsumer = "notifications.identity"

type SessionRetirer interface {
	Handle(context.Context, app.SessionRevocation) error
}

func SubscribeIdentity(receiver SessionRetirer) events.Subscription {
	return events.Subscription{Consumer: IdentityConsumer, Names: []string{"identity.session.revoked.v1"}, Handle: func(ctx context.Context, m events.Envelope) error {
		if m.Validate() != nil || m.Producer != "identity" || m.Name != "identity.session.revoked.v1" || m.AccountID != "" {
			return events.ErrInvalid
		}
		var b struct {
			PrincipalID id.ID  `json:"principal_id"`
			SessionID   id.ID  `json:"session_id"`
			Version     uint64 `json:"version"`
			Reason      string `json:"reason"`
		}
		if err := capability.Decode(m.Data, &b); err != nil {
			return err
		}
		if b.PrincipalID != m.ActorID || b.Version != m.Version {
			return events.ErrInvalid
		}
		return receiver.Handle(ctx, app.SessionRevocation{EventID: m.ID, PrincipalID: b.PrincipalID, SessionID: b.SessionID, Version: b.Version, Reason: b.Reason, OccurredAt: m.OccurredAt})
	}}
}
