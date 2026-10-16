package audit

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/audit/infra/capabilities"
	adapter "github.com/0xsj/mobile-foundry/backend/internal/audit/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
)

func BuildPostgres(ctx context.Context, configuration storage.Config, schema storage.Schema) module.Builder {
	return func(runtime module.Runtime) (module.Unit, error) {
		if runtime.Clock == nil || runtime.Principals == nil || runtime.Capabilities == nil || schema.Name != Schema {
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
		store := adapter.New(pool)
		access := capabilities.Client{Invoker: runtime.Capabilities.Client(Schema)}
		unit := New(store, store, access, runtime.Clock, runtime.Principals)
		unit.Ready = func(ctx context.Context) error { return storage.Failure(pool.Ping(ctx)) }
		unit.Close = func() error { pool.Close(); return nil }
		return unit, nil
	}
}
