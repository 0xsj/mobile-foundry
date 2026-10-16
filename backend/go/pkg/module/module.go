// Package module describes assembled capabilities, without a dependency container.
package module

import (
	"context"
	"net/http"

	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Runtime struct {
	Clock        clock.Clock
	IDs          id.Generator
	Capabilities *capability.Registry
	Events       events.Bus
	Principals   principal.Resolver
}
type Unit struct {
	Name          string
	Routes        func(*http.ServeMux)
	Ready         func(context.Context) error
	Close         func() error
	Provides      []capability.Provision
	Requires      []string
	Subscriptions []events.Subscription
	Start         func() error
	Stop          func() error
}
type Builder func(Runtime) (Unit, error)
