package realtime

import (
	"github.com/0xsj/mobile-foundry/backend/internal/realtime/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/realtime/infra/capabilities"
	"github.com/0xsj/mobile-foundry/backend/internal/realtime/infra/local"
	v1 "github.com/0xsj/mobile-foundry/backend/internal/realtime/transport/websocket/v1"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

const Schema = "realtime"

func Build(runtime module.Runtime) (module.Unit, error) {
	if runtime.Signals == nil || runtime.Principals == nil || runtime.Capabilities == nil {
		return module.Unit{}, events.ErrInvalid
	}
	unit := New(query.Subscribe{Access: capabilities.Client{Invoker: runtime.Capabilities.Client(Schema)}, Feed: local.Feed{Hub: runtime.Signals}}, runtime.Principals)
	unit.Requires = []string{"account.access.v1"}
	return unit, nil
}

// New lets product composition replace authorization and feed adapters together.
func New(subscription query.Subscribe, principals principal.Resolver) module.Unit {
	h := &v1.Handler{Principals: principals, Subscribe: subscription}
	return module.Unit{Name: Schema, Routes: h.Register, Stop: h.Stop}
}
