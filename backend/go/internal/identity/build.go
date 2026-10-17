package identity

import (
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/security"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

func Build(runtime module.Runtime) (module.Unit, error) { return BuildWithMail(nil)(runtime) }
func BuildWithMail(mail app.Mailer) module.Builder {
	return func(runtime module.Runtime) (module.Unit, error) {
		if runtime.Clock == nil || runtime.IDs == nil || runtime.Principals == nil || runtime.Events == nil {
			return module.Unit{}, events.ErrInvalid
		}
		passwords, err := security.NewPasswords()
		if err != nil {
			return module.Unit{}, err
		}
		store := memory.New(runtime.IDs)
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
		return unit, nil
	}
}
