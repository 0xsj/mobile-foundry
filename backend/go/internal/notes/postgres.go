package notes

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/infra/capabilities"
	adapter "github.com/0xsj/mobile-foundry/backend/internal/notes/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
)

func BuildPostgres(ctx context.Context, config storage.Config, schema storage.Schema) module.Builder {
	return func(runtime module.Runtime) (module.Unit, error) {
		if runtime.IDs == nil || runtime.Principals == nil || runtime.Capabilities == nil || schema.Name != Schema {
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
		unit := New(adapter.New(pool), capabilities.Client{Invoker: runtime.Capabilities.Client(Schema)}, runtime.IDs, runtime.Principals, signals{runtime.Signals})
		unit.Ready = pool.Ping
		unit.Close = func() error { pool.Close(); return nil }
		return unit, nil
	}
}
