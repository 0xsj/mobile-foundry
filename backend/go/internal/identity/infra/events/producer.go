package events

import (
	"encoding/json"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func Message(f domain.Fact, metadata events.Metadata, ids id.Generator) (events.Envelope, error) {
	if !f.PrincipalID.Valid() || f.Version == 0 || f.Version > 1<<53-1 {
		return events.Envelope{}, domain.ErrState
	}
	switch f.Action {
	case "principal.registered":
		if f.SessionID != "" || f.Version != 1 || f.Reason != "" {
			return events.Envelope{}, domain.ErrState
		}
	case "session.issued":
		if !f.SessionID.Valid() || f.Version != 1 || f.Reason != "" {
			return events.Envelope{}, domain.ErrState
		}
	case "session.renewed":
		if !f.SessionID.Valid() || f.Version < 2 || f.Reason != "" {
			return events.Envelope{}, domain.ErrState
		}
	case "session.revoked":
		if !f.SessionID.Valid() || f.Version < 2 || (f.Reason != "sign_out" && f.Reason != "refresh_reuse") {
			return events.Envelope{}, domain.ErrState
		}
	default:
		return events.Envelope{}, domain.ErrState
	}
	identity, err := ids.NewID()
	if err != nil {
		return events.Envelope{}, err
	}
	data, err := json.Marshal(struct {
		PrincipalID id.ID  `json:"principal_id"`
		SessionID   id.ID  `json:"session_id,omitempty"`
		Version     uint64 `json:"version"`
		Reason      string `json:"reason,omitempty"`
	}{f.PrincipalID, f.SessionID, f.Version, f.Reason})
	if err != nil {
		return events.Envelope{}, err
	}
	message := events.Envelope{ID: identity, Name: "identity." + f.Action + ".v1", Producer: "identity", ActorID: f.PrincipalID, Version: f.Version, OccurredAt: f.OccurredAt, Metadata: metadata, Data: data}
	if err := message.Validate(); err != nil {
		return events.Envelope{}, err
	}
	return message, nil
}
func Stream(f domain.Fact) id.ID {
	if f.SessionID != "" {
		return f.SessionID
	}
	return f.PrincipalID
}
