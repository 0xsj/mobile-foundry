package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type UnregisterInstallationInput struct {
	Actor    app.InstallationActor
	ID       id.ID
	Proof    string
	Expected uint64
}

func (UnregisterInstallationInput) String() string {
	return "notifications.UnregisterInstallationInput{redacted}"
}
func (UnregisterInstallationInput) GoString() string {
	return "notifications.UnregisterInstallationInput{redacted}"
}

type UnregisterInstallation struct {
	Store  app.Installations
	Proofs app.InstallationProofs
	Clock  clock.Clock
}

func (h UnregisterInstallation) Handle(ctx context.Context, input UnregisterInstallationInput) (domain.Installation, error) {
	now := h.Clock.Now()
	if err := input.Actor.Validate(now); err != nil {
		return domain.Installation{}, err
	}
	if !input.ID.Valid() || input.Expected < 1 || input.Expected > 1<<53-1 {
		return domain.Installation{}, app.ErrInput
	}
	proof, err := h.Proofs.Digest(ctx, input.Proof)
	if err != nil {
		return domain.Installation{}, err
	}
	return h.Store.UnregisterInstallation(ctx, input.Actor, input.ID, proof, input.Expected, now)
}
