package query

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type GetInstallationInput struct {
	Actor app.InstallationActor
	ID    id.ID
	Proof string
}

func (GetInstallationInput) String() string   { return "notifications.GetInstallationInput{redacted}" }
func (GetInstallationInput) GoString() string { return "notifications.GetInstallationInput{redacted}" }

type GetInstallation struct {
	Store  app.Installations
	Proofs app.InstallationProofs
	Clock  clock.Clock
}

func (h GetInstallation) Handle(ctx context.Context, input GetInstallationInput) (domain.Installation, error) {
	now := h.Clock.Now()
	if err := input.Actor.Validate(now); err != nil {
		return domain.Installation{}, err
	}
	if !input.ID.Valid() {
		return domain.Installation{}, app.ErrInput
	}
	proof, err := h.Proofs.Digest(ctx, input.Proof)
	if err != nil {
		return domain.Installation{}, err
	}
	return h.Store.GetInstallation(ctx, input.Actor, input.ID, proof, now)
}
