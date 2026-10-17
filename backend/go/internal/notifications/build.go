package notifications

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/capabilities"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

func Build(runtime module.Runtime) (module.Unit, error) {
	return BuildWithPush(nil)(runtime)
}
func BuildWithPush(gateway app.PushGateway, queued ...bool) module.Builder {
	return func(runtime module.Runtime) (module.Unit, error) {
		if runtime.Clock == nil || runtime.Principals == nil || runtime.Capabilities == nil {
			return module.Unit{}, events.ErrInvalid
		}
		client := capabilities.Client{Invoker: runtime.Capabilities.Client(Schema)}
		return New(memory.New(), client, runtime.Clock, runtime.Principals, PushOptions{Gateway: gateway, Sessions: client, Jobs: len(queued) > 0 && queued[0], IDs: runtime.IDs}), nil
	}
}
