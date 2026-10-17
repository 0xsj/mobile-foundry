package identity

import (
	"context"
	"errors"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	adapter "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/security"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
)

func BuildPostgres(ctx context.Context, configuration storage.Config, schema storage.Schema) module.Builder {
	return BuildPostgresWithMail(ctx, configuration, schema, nil)
}
func BuildPostgresWithMail(ctx context.Context, configuration storage.Config, schema storage.Schema, mail app.Mailer) module.Builder {
	return func(runtime module.Runtime) (module.Unit, error) {
		if runtime.Clock == nil || runtime.IDs == nil || runtime.Principals == nil || runtime.Events == nil || schema.Name != Schema {
			return module.Unit{}, events.ErrInvalid
		}
		passwords, err := security.NewPasswords()
		if err != nil {
			return module.Unit{}, err
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
		unit := New(store, passwords, security.Tokens{}, security.NewGate(), runtime, mail)
		worker := events.NewWorker(&events.Dispatcher{Outbox: store, Delivery: runtime.Events})
		start, stop := unit.Start, unit.Stop
		unit.Ready = store.Ready
		unit.Start = func() error {
			if err := worker.Start(); err != nil {
				return err
			}
			if err := start(); err != nil {
				_ = worker.Close()
				return err
			}
			return nil
		}
		unit.Stop = func() error { return errors.Join(stop(), worker.Close()) }
		unit.Close = func() error { pool.Close(); return nil }
		return unit, nil
	}
}
