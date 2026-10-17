package app

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"time"
)

type Scope struct{ Actor, Account id.ID }

func (s Scope) Valid() bool { return s.Actor.Valid() && s.Account.Valid() }

type AccountAccess interface {
	Allowed(context.Context, id.ID, id.ID) (bool, error)
}

func Authorize(ctx context.Context, access AccountAccess, scope Scope) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if !scope.Valid() {
		return ErrInput
	}
	allowed, err := access.Allowed(ctx, scope.Actor, scope.Account)
	if err != nil {
		return err
	}
	if !allowed {
		return ErrForbidden
	}
	return nil
}

// Delivery is the admitted upload-completed fact. The actor is the sole recipient
// for this catalog type; it is not an audience lookup or an arbitrary send API.
type Delivery struct {
	EventID, AccountID, PrincipalID, FileID id.ID
	OccurredAt                              time.Time
}

func (d Delivery) Validate() error {
	if !d.EventID.Valid() || !d.AccountID.Valid() || !d.PrincipalID.Valid() || !d.FileID.Valid() || d.OccurredAt.IsZero() {
		return ErrInput
	}
	return nil
}
func (d Delivery) Item(sequence uint64, at time.Time) (domain.Item, error) {
	return domain.Restore(domain.Snapshot{ID: d.EventID, AccountID: d.AccountID, PrincipalID: d.PrincipalID, FileID: d.FileID, Sequence: sequence, Type: domain.UploadReady, Category: domain.Media, OccurredAt: d.OccurredAt, CreatedAt: at})
}

type Page struct {
	Items       []domain.Item
	Next        uint64
	UnreadCount uint64
}
type ListInput struct {
	Scope      Scope
	Before     uint64
	Limit      int
	UnreadOnly bool
}

func (i ListInput) Validate() error {
	if !i.Scope.Valid() || i.Before > 1<<53-1 || i.Limit < 1 || i.Limit > 100 {
		return ErrInput
	}
	return nil
}

type Store interface {
	// Deliver commits preference decision, item and source inbox marker together.
	// A suppressed event stays suppressed on redelivery after preferences change.
	Deliver(context.Context, Delivery, time.Time) error
	List(context.Context, ListInput) (Page, error)
	MarkRead(context.Context, Scope, id.ID, time.Time) (domain.Item, error)
	MediaEnabled(context.Context, Scope) (bool, error)
	SetMediaEnabled(context.Context, Scope, bool) error
}
