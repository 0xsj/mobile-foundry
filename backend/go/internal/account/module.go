package account

import (
	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app/query"
	capv1 "github.com/0xsj/mobile-foundry/backend/internal/account/transport/capability/v1"
	httpv1 "github.com/0xsj/mobile-foundry/backend/internal/account/transport/http/v1"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

const Schema = "account"

func New(store interface {
	app.Store
	app.Directory
}, clock clock.Clock, ids id.Generator, principals principal.Resolver) module.Unit {
	handler := httpv1.Handler{Create: command.Create{Store: store, Clock: clock, IDs: ids}, Rename: command.Rename{Store: store, Clock: clock}, Get: query.Get{Store: store}, Bootstrap: command.Bootstrap{Directory: store, Clock: clock, IDs: ids}, List: query.List{Directory: store}, Principals: principals}
	return module.Unit{Name: Schema, Routes: handler.Register, Provides: []capability.Provision{capv1.Provide(query.Access{Store: store})}}
}
