package query

import (
	"context"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
)

// Readiness is an application-owned port. Process composition adapts the
// selected modules' probes; no database or HTTP object enters this handler.
type Readiness interface{ Check(context.Context) error }
type ProbeFunc func(context.Context) error

func (probe ProbeFunc) Check(ctx context.Context) error { return probe(ctx) }

type CheckReady struct{ probe Readiness }

func NewCheckReady(probe Readiness) (*CheckReady, error) {
	if probe == nil {
		return nil, apperrors.New(apperrors.Invalid, "health.invalid_query", "readiness probe is required")
	}
	return &CheckReady{probe: probe}, nil
}
func (handler *CheckReady) Handle(ctx context.Context) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	err := handler.probe.Check(ctx)
	if ctx.Err() != nil {
		return ctx.Err()
	}
	if err != nil {
		return apperrors.New(apperrors.Unavailable, "health.not_ready", "readiness probe failed").WithCause(err).WithPublic("The service is not ready.")
	}
	return nil
}
