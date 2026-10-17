// Package domain owns file admission and lifecycle, independent of byte storage.
package domain

import (
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const MaxBytes int64 = 10 * 1024 * 1024

type Status string

const (
	Pending   Status = "pending_upload"
	Available Status = "available"
	Deleted   Status = "deleted"
)

var (
	ErrSpec       = apperrors.New(apperrors.Invalid, "files.invalid_spec", "invalid file specification").WithPublic("Use a name of 1–255 characters, a supported content type, a SHA-256 digest and between 1 byte and 10 MiB of content.")
	ErrState      = apperrors.New(apperrors.Internal, "files.invalid_state", "invalid persisted file state")
	ErrTransition = apperrors.New(apperrors.Conflict, "files.invalid_transition", "file lifecycle transition refused").WithPublic("This file cannot perform that operation in its current state.")
)

type Spec struct {
	Name, ContentType string
	ByteSize          int64
	SHA256            string
}

func Normalize(s Spec) (Spec, error) {
	s.Name = strings.TrimSpace(s.Name)
	if !utf8.ValidString(s.Name) || utf8.RuneCountInString(s.Name) < 1 || utf8.RuneCountInString(s.Name) > 255 || strings.ContainsAny(s.Name, "/\\") || s.ByteSize < 1 || s.ByteSize > MaxBytes {
		return Spec{}, ErrSpec
	}
	for _, r := range s.Name {
		if unicode.IsControl(r) {
			return Spec{}, ErrSpec
		}
	}
	switch s.ContentType {
	case "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf", "application/octet-stream":
	default:
		return Spec{}, ErrSpec
	}
	if len(s.SHA256) != 64 || strings.IndexFunc(s.SHA256, func(r rune) bool { return !(r >= '0' && r <= '9' || r >= 'a' && r <= 'f') }) >= 0 {
		return Spec{}, ErrSpec
	}
	return s, nil
}

// Snapshot is a detached value; File's state is changed only by its transitions.
type Snapshot struct {
	ID, AccountID, OwnerID id.ID
	Spec                   Spec
	Status                 Status
	Version                uint64
	CreatedAt, UpdatedAt   time.Time
}
type File struct{ state Snapshot }

func New(identity, account, owner id.ID, spec Spec, now time.Time) (File, error) {
	s, err := Normalize(spec)
	if err != nil {
		return File{}, err
	}
	now = now.UTC().Truncate(time.Microsecond)
	return Restore(Snapshot{identity, account, owner, s, Pending, 1, now, now})
}
func Restore(s Snapshot) (File, error) {
	spec, err := Normalize(s.Spec)
	validVersion := s.Status == Pending && s.Version == 1 || s.Status == Available && s.Version == 2 || s.Status == Deleted && (s.Version == 2 || s.Version == 3)
	canonical := func(t time.Time) bool {
		return t.Year() >= 1970 && t.Year() <= 9999 && t.Location() == time.UTC && t.Equal(t.Truncate(time.Microsecond))
	}
	if err != nil || spec != s.Spec || !s.ID.Valid() || !s.AccountID.Valid() || !s.OwnerID.Valid() || !validVersion || !canonical(s.CreatedAt) || !canonical(s.UpdatedAt) || s.UpdatedAt.Before(s.CreatedAt) {
		return File{}, ErrState
	}
	return File{s}, nil
}
func (f File) Snapshot() Snapshot { return f.state }
func (f File) Complete(now time.Time) (File, error) {
	if f.state.Status != Pending {
		return File{}, ErrTransition
	}
	s := f.state
	s.Status = Available
	s.Version++
	s.UpdatedAt = now.UTC().Truncate(time.Microsecond)
	return Restore(s)
}
func (f File) Delete(now time.Time) (File, error) {
	if f.state.Status == Deleted {
		return File{}, ErrTransition
	}
	s := f.state
	s.Status = Deleted
	s.Version++
	s.UpdatedAt = now.UTC().Truncate(time.Microsecond)
	return Restore(s)
}

type Fact struct {
	FileID, AccountID, ActorID id.ID
	Action                     string
	Version                    uint64
	OccurredAt                 time.Time
}

func (f File) Fact(actor id.ID) Fact {
	action := "upload_requested"
	if f.state.Status == Available {
		action = "upload_completed"
	}
	if f.state.Status == Deleted {
		action = "deleted"
	}
	return Fact{f.state.ID, f.state.AccountID, actor, action, f.state.Version, f.state.UpdatedAt}
}
