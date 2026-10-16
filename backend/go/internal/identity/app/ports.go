package app

import (
	"context"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

var ErrExists = apperrors.New(apperrors.Conflict, "identity.login_exists", "login is already registered").WithPublic("That login is already registered.")
var ErrCapacity = apperrors.New(apperrors.Unavailable, "identity.memory_capacity", "memory identity profile is full")
var ErrBusy = apperrors.New(apperrors.RateLimited, "identity.attempt_limited", "identity attempt budget exhausted").WithPublic("Wait briefly before trying again.")

type Store interface {
	ByLogin(context.Context, string) (domain.Principal, bool, error)
	ByID(context.Context, id.ID) (domain.Principal, bool, error)
	Register(context.Context, domain.Principal, events.Metadata) error
	Issue(context.Context, domain.Session, events.Metadata) error
	// Rotate commits reuse revocation before returning reused=true. An error is a failed operation.
	Rotate(context.Context, string, string, string, time.Time, events.Metadata) (domain.Session, bool, error)
	Revoke(context.Context, string, time.Time, events.Metadata) error
	Access(context.Context, string, time.Time) (id.ID, error)
}
type Passwords interface {
	Hash(context.Context, string) (string, error)
	Verify(context.Context, string, string) (bool, error)
	Dummy(context.Context, string) error
}
type Tokens interface {
	Issue(context.Context, string) (string, string, error)
	Digest(context.Context, string, string) (string, error)
}
type Grant struct {
	Principal       domain.Principal
	Session         domain.Session
	access, refresh string
}

func NewGrant(p domain.Principal, s domain.Session, access, refresh string) Grant {
	return Grant{p, s, access, refresh}
}
func (g Grant) AccessToken() string  { return g.access }
func (g Grant) RefreshToken() string { return g.refresh }
func (Grant) String() string         { return "identity.Grant{redacted}" }
func (Grant) GoString() string       { return "identity.Grant{redacted}" }
