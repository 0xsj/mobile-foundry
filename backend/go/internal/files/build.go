package files

import (
	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/capabilities"
	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/objects"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

func Build(runtime module.Runtime) (module.Unit, error) {
	if runtime.Clock == nil || runtime.IDs == nil || runtime.Principals == nil || runtime.Events == nil || runtime.Capabilities == nil {
		return module.Unit{}, events.ErrInvalid
	}
	store := memory.New(runtime.IDs)
	unit := New(store, objects.NewMemory(), capabilities.Client{Invoker: runtime.Capabilities.Client(Schema)}, runtime)
	worker := events.NewWorker(&events.Dispatcher{Outbox: store, Delivery: runtime.Events})
	unit.Ready, unit.Start, unit.Stop = store.Ready, worker.Start, worker.Close
	return unit, nil
}
