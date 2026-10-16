package command

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
)

type Record struct {
	Store app.Store
	Clock clock.Clock
}

func (h Record) Handle(ctx context.Context, consumer string, evidence domain.Evidence) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	entry, err := domain.New(evidence, h.Clock.Now())
	if err != nil {
		return err
	}
	return h.Store.Record(ctx, consumer, entry)
}
