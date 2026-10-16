package app

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

var (
	ErrDenied   = apperrors.New(apperrors.Forbidden, "audit.access_denied", "principal cannot read scoped audit evidence").WithPublic("You cannot read this account's activity.")
	ErrInput    = apperrors.New(apperrors.Invalid, "audit.invalid_query", "invalid audit query position or limit").WithPublic("Use a valid activity cursor and a limit from 1 to 100.")
	ErrReplay   = apperrors.New(apperrors.Invalid, "audit.event_conflict", "event ID was reused with different evidence")
	ErrCapacity = apperrors.New(apperrors.Unavailable, "audit.memory_capacity", "memory audit profile is full")
)

type Record struct {
	Sequence uint64
	Entry    domain.Entry
}
type Page struct {
	Records []Record
	Next    uint64
}
type Store interface {
	// Record commits evidence and the consumer/event marker atomically; replay is success.
	Record(context.Context, string, domain.Entry) error
	List(context.Context, id.ID, uint64, int) (Page, error)
}
type AccountAccess interface {
	Allowed(context.Context, id.ID, id.ID) (bool, error)
}
type PrincipalStore interface {
	ListPrincipal(context.Context, id.ID, uint64, int) (Page, error)
}
