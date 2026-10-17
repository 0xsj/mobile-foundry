package domain

import (
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

const UploadReady = "files.upload_ready"
const Media = "media"

var ErrState = apperrors.New(apperrors.Internal, "notifications.invalid_state", "invalid notification state")

// Snapshot contains identifiers only. Rendering and file access belong to clients.
type Snapshot struct {
	ID, AccountID, PrincipalID, FileID id.ID
	Sequence                           uint64
	Type, Category                     string
	OccurredAt, CreatedAt, ReadAt      time.Time
}
type Item struct{ state Snapshot }

func Restore(s Snapshot) (Item, error) {
	if !s.ID.Valid() || !s.AccountID.Valid() || !s.PrincipalID.Valid() || !s.FileID.Valid() || s.Sequence == 0 || s.Sequence > 1<<53-1 || s.Type != UploadReady || s.Category != Media || s.OccurredAt.IsZero() || s.CreatedAt.IsZero() {
		return Item{}, ErrState
	}
	s.OccurredAt = s.OccurredAt.UTC().Truncate(time.Microsecond)
	s.CreatedAt = s.CreatedAt.UTC().Truncate(time.Microsecond)
	if !s.ReadAt.IsZero() {
		s.ReadAt = s.ReadAt.UTC().Truncate(time.Microsecond)
	}
	return Item{state: s}, nil
}
func (i Item) Snapshot() Snapshot { return i.state }

// MarkRead preserves the first acknowledgement, including across repeated requests.
func (i Item) MarkRead(at time.Time) (Item, error) {
	if at.IsZero() {
		return Item{}, ErrState
	}
	s := i.state
	if s.ReadAt.IsZero() {
		s.ReadAt = at.UTC().Truncate(time.Microsecond)
	}
	return Restore(s)
}
