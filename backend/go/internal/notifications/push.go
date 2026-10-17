package notifications

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/capabilities"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

// NewPushPreview composes the optional application use case after module assembly.
// The ordinary API does not enable a gateway, public send route or worker.
func NewPushPreview(store app.PushTargets, runtime module.Runtime, gateway app.PushGateway) (command.SendPushPreview, error) {
	if store == nil || runtime.Clock == nil || runtime.Capabilities == nil || gateway == nil {
		return command.SendPushPreview{}, capability.ErrInvalid
	}
	if !runtime.Capabilities.Provides(capabilities.SessionEligibilityCapability) {
		return command.SendPushPreview{}, capability.ErrMissing
	}
	return command.SendPushPreview{Targets: store, Sessions: capabilities.Client{Invoker: runtime.Capabilities.Client(Schema)}, Gateway: gateway, Clock: runtime.Clock}, nil
}
