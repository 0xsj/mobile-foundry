package account

import (
	"context"

	adapter "github.com/0xsj/mobile-foundry/backend/internal/account/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
)

func BuildPostgres(ctx context.Context, configuration storage.Config, schema storage.Schema) module.Builder {
	return func(runtime module.Runtime) (module.Unit, error) {
		if runtime.Clock == nil || runtime.IDs == nil || runtime.Principals == nil || runtime.Events == nil || schema.Name != Schema {
			return module.Unit{}, events.ErrInvalid
		}
		pool, err := storage.Open(ctx, configuration)
		if err != nil {
			return module.Unit{}, err
		}
		if err := schema.Check(ctx, pool); err != nil {
			pool.Close()
			return module.Unit{}, err
		}
		store, err := adapter.New(pool, runtime.IDs)
		if err != nil {
			pool.Close()
			return module.Unit{}, err
		}
		unit := New(store, runtime.Clock, runtime.IDs, runtime.Principals)
		worker := events.NewWorker(&events.Dispatcher{Outbox: store, Delivery: runtime.Events})
		unit.Ready, unit.Start, unit.Stop = store.Ready, worker.Start, worker.Close
		unit.Close = func() error { pool.Close(); return nil }
		return unit, nil
	}
}
