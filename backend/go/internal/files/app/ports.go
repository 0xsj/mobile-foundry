package app

import (
	"context"
	"regexp"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type Scope struct{ Actor, Account id.ID }
type AccountAccess interface {
	Allowed(context.Context, id.ID, id.ID) (bool, error)
}

func Authorize(ctx context.Context, access AccountAccess, s Scope) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if !s.Actor.Valid() || !s.Account.Valid() {
		return ErrInput
	}
	allowed, err := access.Allowed(ctx, s.Actor, s.Account)
	if err != nil {
		return err
	}
	if !allowed {
		return ErrForbidden
	}
	return nil
}

const (
	Request  = "request_upload"
	Complete = "complete_upload"
	Delete   = "delete"
)

// Command holds admitted business input. Candidate IDs and time are excluded
// from receipt fingerprints, so retries preserve the caller's original intent.
type Command struct {
	Action, Key string
	FileID      id.ID
	Spec        domain.Spec
	Expected    uint64
}

var keyPattern = regexp.MustCompile(`^[A-Za-z0-9._:-]{1,128}$`)

func ValidKey(key string) bool { return keyPattern.MatchString(key) }
func (c Command) Validate() error {
	if c.Key != "" && !keyPattern.MatchString(c.Key) {
		return ErrInput
	}
	if c.Action == Request {
		s, err := domain.Normalize(c.Spec)
		if err != nil {
			return err
		}
		if s != c.Spec || c.FileID != "" || c.Expected != 0 {
			return ErrInput
		}
		return nil
	}
	if (c.Action != Complete && c.Action != Delete) || !c.FileID.Valid() || c.Expected < 1 || c.Expected > 3 || c.Spec != (domain.Spec{}) {
		return ErrInput
	}
	return nil
}

type Receipt struct {
	Key, Action string
	Actor       id.ID
	File        domain.Snapshot
}
type Store interface {
	// Apply checks replay before CAS/existence and commits state, receipt and fact
	// atomically. Adapters own locking; no transaction escapes into application code.
	Apply(context.Context, Scope, Command, id.ID, time.Time, events.Metadata) (Receipt, error)
	Replay(context.Context, Scope, Command) (Receipt, bool, error)
	Receipt(context.Context, Scope, string) (Receipt, bool, error)
	Get(context.Context, id.ID, id.ID) (domain.File, bool, error)
	List(context.Context, id.ID, id.ID, int) ([]domain.File, error)
}

// Objects stores immutable admitted bytes. Put must verify size/digest and may
// only repeat the identical content. Get verifies stored bytes before returning.
// Keys derive solely from admitted IDs and never from a user filename.
type Objects interface {
	Put(context.Context, domain.Snapshot, []byte) error
	Get(context.Context, domain.Snapshot) ([]byte, error)
	Delete(context.Context, domain.Snapshot) error
}

// Plan is the store's pure write policy; both adapters apply it under their guard.
func Plan(scope Scope, c Command, current *domain.File, candidate id.ID, now time.Time) (domain.File, error) {
	if err := c.Validate(); err != nil {
		return domain.File{}, err
	}
	if c.Action == Request {
		return domain.New(candidate, scope.Account, scope.Actor, c.Spec, now)
	}
	if current == nil || current.Snapshot().Status == domain.Deleted {
		return domain.File{}, ErrMissing
	}
	s := current.Snapshot()
	if s.AccountID != scope.Account || s.ID != c.FileID {
		return domain.File{}, domain.ErrState
	}
	if s.Version != c.Expected {
		return domain.File{}, ErrConflict
	}
	if c.Action == Complete {
		return current.Complete(now)
	}
	return current.Delete(now)
}
