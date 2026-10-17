package identity

import (
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/capabilities"
	capv1 "github.com/0xsj/mobile-foundry/backend/internal/identity/transport/capability/v1"
	capv2 "github.com/0xsj/mobile-foundry/backend/internal/identity/transport/capability/v2"
	httpv1 "github.com/0xsj/mobile-foundry/backend/internal/identity/transport/http/v1"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

const Schema = "identity"
const CredentialCapability = capv2.Name

func CredentialResolver(invoker capability.Invoker) principal.Resolver {
	return capabilities.Resolver{Invoker: invoker}
}
func New(store interface {
	app.Store
	query.SessionAccess
	query.SessionStatus
}, passwords app.Passwords, tokens app.Tokens, gate httpv1.AttemptGate, runtime module.Runtime) module.Unit {
	h := httpv1.Handler{RegisterPrincipal: command.Register{Store: store, Passwords: passwords, IDs: runtime.IDs, Clock: runtime.Clock}, SignIn: command.SignIn{Store: store, Passwords: passwords, Tokens: tokens, IDs: runtime.IDs, Clock: runtime.Clock}, Renew: command.Renew{Store: store, Tokens: tokens, Clock: runtime.Clock}, SignOut: command.SignOut{Store: store, Tokens: tokens, Clock: runtime.Clock}, Me: query.Me{Store: store}, Principals: runtime.Principals, Gate: gate}
	return module.Unit{Name: Schema, Routes: h.Register, Provides: []capability.Provision{capv1.Provide(query.Resolve{Store: store, Tokens: tokens, Clock: runtime.Clock}), capv2.Provide(query.ResolveSession{Store: store, Tokens: tokens, Clock: runtime.Clock}), capv1.ProvideSessionEligibility(query.SessionEligibility{Store: store, Clock: runtime.Clock})}}
}
