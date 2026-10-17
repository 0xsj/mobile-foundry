package files

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/capabilities"
	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/objects"
	adapter "github.com/0xsj/mobile-foundry/backend/internal/files/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
)

func BuildPostgres(ctx context.Context, config storage.Config, schema storage.Schema, directory string) module.Builder {
	return func(runtime module.Runtime) (module.Unit, error) {
		if runtime.Clock == nil || runtime.IDs == nil || runtime.Principals == nil || runtime.Events == nil || runtime.Capabilities == nil || schema.Name != Schema {
			return module.Unit{}, events.ErrInvalid
		}
		pool, err := storage.Open(ctx, config)
		if err != nil {
			return module.Unit{}, err
		}
		if err := schema.Check(ctx, pool); err != nil {
			pool.Close()
			return module.Unit{}, err
		}
		blobs, err := objects.NewDirectory(directory)
		if err != nil {
			pool.Close()
			return module.Unit{}, err
		}
		store, err := adapter.New(pool, runtime.IDs)
		if err != nil {
			pool.Close()
			return module.Unit{}, err
		}
		unit := New(store, blobs, capabilities.Client{Invoker: runtime.Capabilities.Client(Schema)}, runtime)
		worker := events.NewWorker(&events.Dispatcher{Outbox: store, Delivery: runtime.Events})
		unit.Ready, unit.Start, unit.Stop = store.Ready, worker.Start, worker.Close
		unit.Close = func() error { pool.Close(); return nil }
		return unit, nil
	}
}
