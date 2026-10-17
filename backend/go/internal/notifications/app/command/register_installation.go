package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
)

type RegisterInstallation struct {
	Store  app.Installations
	Proofs app.InstallationProofs
	Clock  clock.Clock
}

func (h RegisterInstallation) Handle(ctx context.Context, input app.RegisterInstallationInput) (domain.Installation, error) {
	now := h.Clock.Now()
	if err := input.Actor.Validate(now); err != nil {
		return domain.Installation{}, err
	}
	proof, err := h.Proofs.Digest(ctx, input.Proof)
	if err != nil {
		return domain.Installation{}, err
	}
	m := app.InstallationMutation{Actor: input.Actor, ID: input.ID, RegistrationID: input.RegistrationID, ProofDigest: proof, Spec: input.Spec, Expected: input.Expected}
	if err := m.Validate(now); err != nil {
		return domain.Installation{}, err
	}
	return h.Store.RegisterInstallation(ctx, m, now)
}
