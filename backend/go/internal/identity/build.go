package identity

import (
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/security"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

func Build(runtime module.Runtime) (module.Unit, error) {
	if runtime.Clock == nil || runtime.IDs == nil || runtime.Principals == nil || runtime.Events == nil {
		return module.Unit{}, events.ErrInvalid
	}
	passwords, err := security.NewPasswords()
	if err != nil {
		return module.Unit{}, err
	}
	store := memory.New(runtime.IDs)
	unit := New(store, passwords, security.Tokens{}, security.NewGate(), runtime)
	worker := events.NewWorker(&events.Dispatcher{Outbox: store, Delivery: runtime.Events})
	unit.Ready, unit.Start, unit.Stop = store.Ready, worker.Start, worker.Close
	return unit, nil
}
