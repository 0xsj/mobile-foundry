package events

import (
	"encoding/json"

	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func Message(f domain.Fact, metadata events.Metadata, ids id.Generator) (events.Envelope, error) {
	if !f.FileID.Valid() || !f.AccountID.Valid() || !f.ActorID.Valid() || (f.Action != "upload_requested" && f.Action != "upload_completed" && f.Action != "deleted") {
		return events.Envelope{}, events.ErrInvalid
	}
	identity, err := ids.NewID()
	if err != nil {
		return events.Envelope{}, err
	}
	data, err := json.Marshal(struct {
		FileID    id.ID  `json:"file_id"`
		AccountID id.ID  `json:"account_id"`
		Version   uint64 `json:"version"`
	}{f.FileID, f.AccountID, f.Version})
	if err != nil {
		return events.Envelope{}, err
	}
	m := events.Envelope{ID: identity, Name: "files." + f.Action + ".v1", Producer: "files", AccountID: f.AccountID, ActorID: f.ActorID, Version: f.Version, OccurredAt: f.OccurredAt, Metadata: metadata, Data: data}
	return m, m.Validate()
}
