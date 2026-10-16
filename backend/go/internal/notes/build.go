package notes

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notes/infra/capabilities"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

func Build(runtime module.Runtime) (module.Unit, error) {
	if runtime.IDs == nil || runtime.Principals == nil || runtime.Capabilities == nil {
		return module.Unit{}, events.ErrInvalid
	}
	return New(memory.New(), capabilities.Client{Invoker: runtime.Capabilities.Client(Schema)}, runtime.IDs, runtime.Principals), nil
}
