package audit

import (
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app/query"
	receiver "github.com/0xsj/mobile-foundry/backend/internal/audit/infra/events"
	httpv1 "github.com/0xsj/mobile-foundry/backend/internal/audit/transport/http/v1"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

const Schema = "audit"

func New(store app.Store, personal app.PrincipalStore, access app.AccountAccess, clock clock.Clock, principals principal.Resolver) module.Unit {
	handler := httpv1.Handler{List: query.List{Store: store, Access: access}, Personal: query.ListPrincipal{Store: personal}, Principals: principals}
	recorder := command.Record{Store: store, Clock: clock}
	return module.Unit{Name: Schema, Routes: handler.Register, Requires: []string{"account.access.v1"}, Subscriptions: []events.Subscription{receiver.Subscribe(recorder), receiver.SubscribeIdentity(recorder), receiver.SubscribeFiles(recorder)}}
}
