package domain

import (
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const MaxVersion uint64 = 1<<53 - 1

var ErrName = apperrors.New(apperrors.Invalid, "account.invalid_name", "invalid account name").WithPublic("Provide an account name of 1 to 80 characters.").WithFields(map[string]string{"name": "Use 1 to 80 characters without control characters."})
var ErrState = apperrors.New(apperrors.Internal, "account.invalid_state", "invalid account state")

type Account struct {
	identity, owner      id.ID
	name                 string
	version              uint64
	createdAt, updatedAt time.Time
}
type Snapshot struct {
	ID                   id.ID
	OwnerID              id.ID
	Name                 string
	Version              uint64
	CreatedAt, UpdatedAt time.Time
}

func Name(value string) (string, error) {
	if !utf8.ValidString(value) {
		return "", ErrName
	}
	value = strings.TrimSpace(value)
	if count := utf8.RuneCountInString(value); count < 1 || count > 80 {
		return "", ErrName
	}
	for _, character := range value {
		if unicode.IsControl(character) {
			return "", ErrName
		}
	}
	return value, nil
}
func New(identity, owner id.ID, name string, now time.Time) (Account, error) {
	name, err := Name(name)
	if err != nil {
		return Account{}, err
	}
	now = now.UTC().Truncate(time.Microsecond)
	return Restore(Snapshot{ID: identity, OwnerID: owner, Name: name, Version: 1, CreatedAt: now, UpdatedAt: now})
}

// Restore rejects noncanonical state instead of hiding corruption by normalization.
func Restore(s Snapshot) (Account, error) {
	name, err := Name(s.Name)
	if err != nil || name != s.Name || !s.ID.Valid() || !s.OwnerID.Valid() || s.Version == 0 || s.Version > MaxVersion || !canonicalTime(s.CreatedAt) || !canonicalTime(s.UpdatedAt) || s.UpdatedAt.Before(s.CreatedAt) {
		return Account{}, ErrState
	}
	return Account{s.ID, s.OwnerID, s.Name, s.Version, s.CreatedAt, s.UpdatedAt}, nil
}
func canonicalTime(value time.Time) bool {
	return value.Year() >= 1970 && value.Year() <= 9999 && value.Location() == time.UTC && value.Equal(value.Truncate(time.Microsecond))
}
func (a Account) Snapshot() Snapshot {
	return Snapshot{a.identity, a.owner, a.name, a.version, a.createdAt, a.updatedAt}
}
func (a Account) OwnedBy(principalID id.ID) bool {
	return principalID.Valid() && a.owner == principalID
}
func (a Account) Rename(name string, now time.Time) (Account, error) {
	name, err := Name(name)
	if err != nil {
		return Account{}, err
	}
	s := a.Snapshot()
	s.Name = name
	s.Version++
	s.UpdatedAt = now.UTC().Truncate(time.Microsecond)
	return Restore(s)
}

type Action string

const (
	Created Action = "created"
	Renamed Action = "renamed"
)

// Fact contains the selected immutable evidence, excluding the account's name.
type Fact struct {
	AccountID, ActorID id.ID
	Version            uint64
	Action             Action
	OccurredAt         time.Time
}

func (a Account) Fact(action Action, actor id.ID) (Fact, error) {
	if !a.OwnedBy(actor) || (action != Created && action != Renamed) || (action == Created && a.version != 1) || (action == Renamed && a.version < 2) {
		return Fact{}, ErrState
	}
	return Fact{a.identity, actor, a.version, action, a.updatedAt}, nil
}
