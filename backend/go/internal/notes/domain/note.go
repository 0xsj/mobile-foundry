// Package domain admits Notes values without transport or persistence dependencies.
package domain

import (
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"unicode/utf8"
)

const MaxVersion uint64 = 9007199254740991
const (
	Create = "create"
	Update = "update"
	Delete = "delete"
)

var (
	ErrTitle    = apperrors.New(apperrors.Invalid, "notes.invalid_title", "invalid note title").WithPublic("Check the highlighted field.").WithFields(map[string]string{"title": "Enter a title of 200 characters or fewer."})
	ErrWrite    = apperrors.New(apperrors.Invalid, "notes.invalid_write", "invalid remote write").WithPublic("Use an admitted command identifier, operation and version.")
	ErrState    = apperrors.New(apperrors.Internal, "notes.invalid_state", "invalid persisted Notes state")
	ErrMissing  = apperrors.New(apperrors.NotFound, "notes.not_found", "note absent").WithPublic("This note no longer exists. Refresh your notes.")
	ErrConflict = apperrors.New(apperrors.Conflict, "notes.version_conflict", "note version changed").WithPublic("This note changed. Refresh before editing again.")
	ErrKey      = apperrors.New(apperrors.Conflict, "notes.command_conflict", "committed command has different input").WithPublic("This command identifier already belongs to a different write.")
)

func ValidTitle(title string) bool {
	if !utf8.ValidString(title) || utf8.RuneCountInString(title) > 200 {
		return false
	}
	for _, r := range title {
		if r != '\t' && r != '\n' && r != '\r' && r != ' ' {
			return true
		}
	}
	return false
}

type Note struct {
	ID      id.ID  `json:"id"`
	Title   string `json:"title"`
	Version uint64 `json:"version"`
}

func (n Note) Valid() bool {
	return n.ID.Valid() && ValidTitle(n.Title) && n.Version > 0 && n.Version <= MaxVersion
}

// The command ID identifies one admitted input. Generated create IDs are not input.
type Write struct {
	CommandID id.ID
	Action    string
	NoteID    id.ID
	Title     string
	Expected  uint64
}

func (w Write) Validate() error {
	if !w.CommandID.Valid() {
		return ErrWrite
	}
	switch w.Action {
	case Create:
		if w.NoteID != "" || w.Expected != 0 {
			return ErrWrite
		}
	case Update, Delete:
		if !w.NoteID.Valid() || w.Expected == 0 || w.Expected >= MaxVersion {
			return ErrWrite
		}
	default:
		return ErrWrite
	}
	if w.Action == Delete {
		if w.Title != "" {
			return ErrWrite
		}
		return nil
	}
	if !ValidTitle(w.Title) {
		return ErrTitle
	}
	return nil
}

// Receipt is immutable commit evidence, including the historical title for non-deletes.
// Title is a value (not a pointer) so memory snapshots cannot alias provider state.
type Receipt struct {
	CommandID   id.ID  `json:"command_id"`
	AccountID   id.ID  `json:"account_id"`
	PrincipalID id.ID  `json:"principal_id"`
	Action      string `json:"action"`
	NoteID      id.ID  `json:"note_id"`
	Version     uint64 `json:"version"`
	Title       string `json:"title"`
}

func (r Receipt) Valid() bool {
	if !r.CommandID.Valid() || !r.AccountID.Valid() || !r.PrincipalID.Valid() || !r.NoteID.Valid() || r.Version == 0 || r.Version > MaxVersion {
		return false
	}
	switch r.Action {
	case Create:
		return r.Version == 1 && ValidTitle(r.Title)
	case Update:
		return r.Version >= 2 && ValidTitle(r.Title)
	case Delete:
		return r.Version >= 2 && r.Title == ""
	}
	return false
}

// Decide is shared by both stores; persistence supplies the locked current row.
func Decide(w Write, current *Note, candidate id.ID, accountID, actorID id.ID) (Receipt, error) {
	if err := w.Validate(); err != nil {
		return Receipt{}, err
	}
	r := Receipt{CommandID: w.CommandID, AccountID: accountID, PrincipalID: actorID, Action: w.Action, NoteID: w.NoteID, Title: w.Title}
	if w.Action == Create {
		if !candidate.Valid() || current != nil {
			return Receipt{}, ErrState
		}
		r.NoteID = candidate
		r.Version = 1
	} else {
		if current == nil {
			return Receipt{}, ErrMissing
		}
		if !current.Valid() || current.ID != w.NoteID {
			return Receipt{}, ErrState
		}
		if current.Version != w.Expected {
			return Receipt{}, ErrConflict
		}
		r.Version = current.Version + 1
	}
	if !r.Valid() {
		return Receipt{}, ErrState
	}
	return r, nil
}
