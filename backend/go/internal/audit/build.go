package audit

import (
	"github.com/0xsj/mobile-foundry/backend/internal/audit/infra/capabilities"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

func Build(runtime module.Runtime) (module.Unit, error) {
	if runtime.Clock == nil || runtime.Principals == nil || runtime.Capabilities == nil {
		return module.Unit{}, events.ErrInvalid
	}
	store := memory.New()
	access := capabilities.Client{Invoker: runtime.Capabilities.Client(Schema)}
	return New(store, store, access, runtime.Clock, runtime.Principals), nil
}
