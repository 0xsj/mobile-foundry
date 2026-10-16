package events

import (
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func Message(fact domain.Fact, metadata events.Metadata, ids id.Generator) (events.Envelope, error) {
	if fact.Action != domain.Created && fact.Action != domain.Renamed {
		return events.Envelope{}, events.ErrInvalid
	}
	identity, err := ids.NewID()
	if err != nil {
		return events.Envelope{}, err
	}
	payload, err := json.Marshal(struct {
		AccountID id.ID  `json:"account_id"`
		Version   uint64 `json:"version"`
	}{fact.AccountID, fact.Version})
	if err != nil {
		return events.Envelope{}, err
	}
	message := events.Envelope{ID: identity, Name: "account." + string(fact.Action) + ".v1", Producer: "account", AccountID: fact.AccountID, ActorID: fact.ActorID, Version: fact.Version, OccurredAt: fact.OccurredAt, Metadata: metadata, Data: payload}
	return message, message.Validate()
}
