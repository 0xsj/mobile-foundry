package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type ChangePassword struct {
	Store     app.Store
	Recovery  app.RecoveryStore
	Passwords app.Passwords
	Clock     clock.Clock
}
type ChangePasswordInput struct {
	PrincipalID                  id.ID
	CurrentPassword, NewPassword string
}

func (ChangePasswordInput) String() string { return "identity.ChangePasswordInput{redacted}" }
func (h ChangePassword) Handle(ctx context.Context, in ChangePasswordInput, m events.Metadata) error {
	if err := domain.Password(in.NewPassword); err != nil {
		return err
	}
	if domain.Password(in.CurrentPassword) != nil {
		return domain.ErrCredential
	}
	p, found, err := h.Store.ByID(ctx, in.PrincipalID)
	if err != nil {
		return err
	}
	if !found {
		return domain.ErrCredential
	}
	matched, err := h.Passwords.Verify(ctx, p.Snapshot().PasswordHash, in.CurrentPassword)
	if err != nil {
		return err
	}
	if !matched {
		return domain.ErrCredential
	}
	hash, err := h.Passwords.Hash(ctx, in.NewPassword)
	if err != nil {
		return err
	}
	return h.Recovery.ChangePassword(ctx, in.PrincipalID, p.Snapshot().PasswordHash, hash, h.Clock.Now(), m)
}

func (v ChangePasswordInput) GoString() string { return v.String() }
