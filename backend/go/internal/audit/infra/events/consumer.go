package events

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const Consumer = "audit.account"

type Recorder interface {
	Handle(context.Context, string, domain.Evidence) error
}

func Subscribe(recorder Recorder) events.Subscription {
	return events.Subscription{Consumer: Consumer, Names: []string{"account.created.v1", "account.renamed.v1"}, Handle: func(ctx context.Context, message events.Envelope) error {
		if err := message.Validate(); err != nil {
			return err
		}
		if message.Producer != "account" || (message.Name != "account.created.v1" && message.Name != "account.renamed.v1") {
			return events.ErrInvalid
		}
		var data struct {
			AccountID id.ID  `json:"account_id"`
			Version   uint64 `json:"version"`
		}
		if err := capability.Decode(message.Data, &data); err != nil {
			return err
		}
		if data.AccountID != message.AccountID || data.Version != message.Version {
			return events.ErrInvalid
		}
		action := "account.created"
		if message.Name == "account.renamed.v1" {
			action = "account.renamed"
		}
		return recorder.Handle(ctx, Consumer, domain.Evidence{EventID: message.ID, AccountID: message.AccountID, ActorID: message.ActorID, Action: action, Version: message.Version, OccurredAt: message.OccurredAt, RequestID: message.Metadata.RequestID, CorrelationID: message.Metadata.CorrelationID})
	}}
}
