package identity

import (
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/capabilities"
	email "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/email"
	capv1 "github.com/0xsj/mobile-foundry/backend/internal/identity/transport/capability/v1"
	capv2 "github.com/0xsj/mobile-foundry/backend/internal/identity/transport/capability/v2"
	httpv1 "github.com/0xsj/mobile-foundry/backend/internal/identity/transport/http/v1"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"log/slog"
	"net/http"
)

const Schema = "identity"
const CredentialCapability = capv2.Name

func CredentialResolver(invoker capability.Invoker) principal.Resolver {
	return capabilities.Resolver{Invoker: invoker}
}
func New(store interface {
	app.Store
	app.RecoveryStore
	query.SessionAccess
	query.SessionStatus
}, passwords app.Passwords, tokens app.Tokens, gate httpv1.AttemptGate, runtime module.Runtime, mailers ...app.Mailer) module.Unit {
	var mail app.Mailer = email.Disabled{}
	if len(mailers) > 0 && mailers[0] != nil {
		mail = mailers[0]
	}
	h := httpv1.Handler{RegisterPrincipal: command.Register{Store: store, Passwords: passwords, IDs: runtime.IDs, Clock: runtime.Clock}, SignIn: command.SignIn{Store: store, Passwords: passwords, Tokens: tokens, IDs: runtime.IDs, Clock: runtime.Clock}, Renew: command.Renew{Store: store, Tokens: tokens, Clock: runtime.Clock}, SignOut: command.SignOut{Store: store, Tokens: tokens, Clock: runtime.Clock}, Me: query.Me{Store: store}, Principals: runtime.Principals, Gate: gate}
	resetQueue := email.NewResetQueue(command.RequestPasswordReset{Recovery: store, Tokens: tokens, Mail: mail, Clock: runtime.Clock}, func(err error) {
		v := apperrors.Public(err)
		slog.Warn("identity recovery request could not complete", "kind", v.Kind, "code", v.Code)
	})
	recovery := httpv1.RecoveryHandler{RequestVerification: command.RequestEmailVerification{Store: store, Recovery: store, Passwords: passwords, Tokens: tokens, Mail: mail, Clock: runtime.Clock}, Verify: command.VerifyEmail{Store: store, Tokens: tokens, Clock: runtime.Clock}, RequestReset: resetQueue, Reset: command.ResetPassword{Store: store, Tokens: tokens, Passwords: passwords, Clock: runtime.Clock}, Change: command.ChangePassword{Store: store, Recovery: store, Passwords: passwords, Clock: runtime.Clock}, Store: store, Principals: runtime.Principals, Admission: h}
	return module.Unit{Name: Schema, Start: resetQueue.Start, Stop: resetQueue.Close, Routes: func(mux *http.ServeMux) { h.Register(mux); recovery.Register(mux) }, Provides: []capability.Provision{capv1.Provide(query.Resolve{Store: store, Tokens: tokens, Clock: runtime.Clock}), capv2.Provide(query.ResolveSession{Store: store, Tokens: tokens, Clock: runtime.Clock}), capv1.ProvideSessionEligibility(query.SessionEligibility{Store: store, Clock: runtime.Clock})}}
}
