package app

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

var (
	ErrNotFound = apperrors.New(apperrors.NotFound, "account.not_found", "account is absent").WithPublic("The account was not found.")
	ErrDenied   = apperrors.New(apperrors.Forbidden, "account.access_denied", "principal is not the account owner").WithPublic("You cannot access this account.")
	ErrConflict = apperrors.New(apperrors.Conflict, "account.version_conflict", "account version changed").WithPublic("The account changed. Reload it before saving.")
	ErrCapacity = apperrors.New(apperrors.Unavailable, "account.memory_capacity", "memory profile capacity exhausted").WithPublic("The local account store is full.")
	ErrInput    = apperrors.New(apperrors.Invalid, "account.invalid_page", "invalid account page or cursor").WithPublic("Send a valid account cursor and a limit from 1 to 100.")
)

// Writes atomically persist admitted state and required integration messages.
// Get distinguishes successful absence from dependency failure.
type Store interface {
	Get(context.Context, id.ID) (domain.Account, bool, error)
	Create(context.Context, domain.Account, domain.Fact, events.Metadata) error
	Rename(context.Context, domain.Account, uint64, domain.Fact, events.Metadata) error
}

// Directory owns the personal-account mapping and owner-filtered discovery.
// Bootstrap atomically commits mapping, candidate and creation fact, or returns
// the current mapped account without writing. Ordinary Create never sets it.
// List returns at most limit accounts in ascending canonical UUID order.
type Directory interface {
	Bootstrap(context.Context, domain.Account, domain.Fact, events.Metadata) (domain.Account, bool, error)
	List(context.Context, id.ID, id.ID, int) ([]domain.Account, error)
}

type BootstrapResult struct {
	Account domain.Account
	Created bool
}

type Page struct {
	Accounts []domain.Account
	Next     id.ID
}
