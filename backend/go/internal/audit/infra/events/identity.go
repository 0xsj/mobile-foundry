package events

import (
	"context"
	"strings"

	"github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const IdentityConsumer = "audit.identity"

func SubscribeIdentity(recorder Recorder) events.Subscription {
	return events.Subscription{Consumer: IdentityConsumer, Names: []string{"identity.principal.registered.v1", "identity.session.issued.v1", "identity.session.renewed.v1", "identity.session.revoked.v1"}, Handle: func(ctx context.Context, message events.Envelope) error {
		if message.Validate() != nil || message.Producer != "identity" || message.AccountID != "" {
			return events.ErrInvalid
		}
		var data struct {
			PrincipalID id.ID  `json:"principal_id"`
			SessionID   id.ID  `json:"session_id,omitempty"`
			Version     uint64 `json:"version"`
			Reason      string `json:"reason,omitempty"`
		}
		if err := capability.Decode(message.Data, &data); err != nil {
			return err
		}
		if data.PrincipalID != message.ActorID || data.Version != message.Version {
			return events.ErrInvalid
		}
		e := domain.Evidence{EventID: message.ID, ActorID: message.ActorID, Action: strings.TrimSuffix(message.Name, ".v1"), Version: message.Version, OccurredAt: message.OccurredAt, RequestID: message.Metadata.RequestID, CorrelationID: message.Metadata.CorrelationID, PrincipalID: data.PrincipalID, SessionID: data.SessionID, Reason: data.Reason}
		return recorder.Handle(ctx, IdentityConsumer, e)
	}}
}
