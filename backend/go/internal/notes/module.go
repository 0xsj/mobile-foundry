package notes

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notes/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/app/query"
	httpv1 "github.com/0xsj/mobile-foundry/backend/internal/notes/transport/http/v1"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

const Schema = "notes"

func New(store app.Store, access app.AccountAccess, ids id.Generator, principals principal.Resolver) module.Unit {
	h := httpv1.Handler{Write: command.Write{Store: store, Access: access, IDs: ids}, Read: query.Read{Store: store, Access: access}, Principals: principals}
	return module.Unit{Name: Schema, Routes: h.Register, Requires: []string{"account.access.v1"}}
}
