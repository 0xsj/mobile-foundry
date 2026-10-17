package domain

import (
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

var ErrEntry = apperrors.New(apperrors.Invalid, "audit.invalid_entry", "invalid admitted audit evidence")

type Evidence struct {
	EventID, AccountID, ActorID id.ID
	Action                      string
	Version                     uint64
	OccurredAt                  time.Time
	RequestID, CorrelationID    string
	PrincipalID                 id.ID  `json:",omitempty"`
	SessionID                   id.ID  `json:",omitempty"`
	Reason                      string `json:",omitempty"`
	FileID                      id.ID  `json:",omitempty"`
}
type Entry struct {
	evidence   Evidence
	recordedAt time.Time
}

func New(e Evidence, now time.Time) (Entry, error) {
	validScope := false
	if e.AccountID.Valid() && e.PrincipalID == "" && e.SessionID == "" && e.Reason == "" {
		if e.FileID == "" {
			validScope = (e.Action == "account.created" && e.Version == 1) || (e.Action == "account.renamed" && e.Version >= 2)
		} else if e.FileID.Valid() {
			validScope = (e.Action == "files.upload_requested" && e.Version == 1) || (e.Action == "files.upload_completed" && e.Version == 2) || (e.Action == "files.deleted" && (e.Version == 2 || e.Version == 3))
		}
	} else if e.AccountID == "" && e.FileID == "" && e.PrincipalID.Valid() && e.ActorID == e.PrincipalID {
		switch e.Action {
		case "identity.principal.registered":
			validScope = e.SessionID == "" && e.Version == 1 && e.Reason == ""
		case "identity.email.verified", "identity.password.changed", "identity.password.recovered":
			validScope = e.SessionID == "" && e.Version >= 2 && e.Reason == ""
		case "identity.session.issued":
			validScope = e.SessionID.Valid() && e.Version == 1 && e.Reason == ""
		case "identity.session.renewed":
			validScope = e.SessionID.Valid() && e.Version >= 2 && e.Reason == ""
		case "identity.session.revoked":
			validScope = e.SessionID.Valid() && e.Version >= 2 && (e.Reason == "sign_out" || e.Reason == "refresh_reuse" || e.Reason == "password_change" || e.Reason == "password_reset")
		}
	}
	if !e.EventID.Valid() || !validScope || !e.ActorID.Valid() || e.Version == 0 || e.Version > 1<<53-1 || e.OccurredAt.Year() < 1970 || e.OccurredAt.Year() > 9999 || e.OccurredAt.Location() != time.UTC || !e.OccurredAt.Equal(e.OccurredAt.Truncate(time.Microsecond)) || now.Year() < 1970 || now.Year() > 9999 {
		return Entry{}, ErrEntry
	}
	return Entry{evidence: e, recordedAt: now.UTC().Truncate(time.Microsecond)}, nil
}
func (e Entry) Evidence() Evidence    { return e.evidence }
func (e Entry) RecordedAt() time.Time { return e.recordedAt }
