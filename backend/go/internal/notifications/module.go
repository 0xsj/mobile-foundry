package notifications

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/capabilities"
	receiver "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/events"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/security"
	httpv1 "github.com/0xsj/mobile-foundry/backend/internal/notifications/transport/http/v1"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/jobs"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"net/http"
	"time"
)

const Schema = "notifications"

type PushOptions struct {
	Gateway  app.PushGateway
	Sessions app.SessionEligibility
	Jobs     bool
	IDs      id.Generator
}

func New(store interface {
	app.Store
	app.Installations
	app.PushTargets
	app.PushJobs
}, access app.AccountAccess, clock clock.Clock, principals principal.Resolver, options ...PushOptions) module.Unit {
	handler := httpv1.Handler{List: query.ListInbox{Store: store, Access: access}, Read: command.MarkRead{Store: store, Access: access, Clock: clock}, Preferences: query.GetPreferences{Store: store, Access: access}, SetPreference: command.SetMediaPreference{Store: store, Access: access}, Principals: principals}
	installations := httpv1.InstallationsHandler{Register: command.RegisterInstallation{Store: store, Proofs: security.InstallationProofs{}, Clock: clock}, Get: query.GetInstallation{Store: store, Proofs: security.InstallationProofs{}, Clock: clock}, Unregister: command.UnregisterInstallation{Store: store, Proofs: security.InstallationProofs{}, Clock: clock}, Principals: principals}
	var push PushOptions
	if len(options) > 0 {
		push = options[0]
	}
	requires := []string{"account.access.v1"}
	if push.Gateway != nil {
		requires = append(requires, capabilities.SessionEligibilityCapability)
	}
	unit := module.Unit{Name: Schema, Routes: func(mux *http.ServeMux) {
		handler.Register(mux)
		installations.Routes(mux)
		if push.Gateway != nil {
			installations.PreviewRoutes(mux, command.SendPushPreview{Targets: store, Sessions: push.Sessions, Gateway: push.Gateway, Clock: clock})
			if push.Jobs {
				installations.PushJobRoutes(mux, command.QueuePushPreview{Store: store, Targets: store, Clock: clock}, query.GetPushJob{Store: store, Clock: clock})
				installations.PushJobRecoveryRoutes(mux, command.CancelPushJob{Store: store, Clock: clock}, command.ReplacePushJob{Store: store, Targets: store, Clock: clock}, query.GetPushJobReplacement{Store: store, Clock: clock})
			}
		}
	}, Requires: requires, Subscriptions: []events.Subscription{receiver.SubscribeFiles(command.DeliverUpload{Store: store, Clock: clock}), receiver.SubscribeIdentity(command.RetireSession{Store: store})}}
	if push.Jobs {
		run := command.RunPushJob{Store: store, Send: command.SendPushPreview{Targets: store, Sessions: push.Sessions, Gateway: push.Gateway, Clock: clock}, IDs: push.IDs}
		worker := &jobs.Worker{Run: run.Handle, Interval: 100 * time.Millisecond}
		unit.Start = func() error {
			if push.Gateway == nil || push.Sessions == nil || push.IDs == nil {
				return jobs.ErrInvalid
			}
			return worker.Start()
		}
		unit.Stop = worker.Close
	}
	return unit
}
