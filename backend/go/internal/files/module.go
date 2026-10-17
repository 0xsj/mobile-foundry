package files

import (
	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/files/app/query"
	v1 "github.com/0xsj/mobile-foundry/backend/internal/files/transport/http/v1"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

const Schema = "files"

func New(store app.Store, objects app.Objects, access app.AccountAccess, runtime module.Runtime) module.Unit {
	h := v1.Handler{Request: command.RequestUpload{Store: store, Access: access, IDs: runtime.IDs, Clock: runtime.Clock}, Upload: command.UploadContent{Store: store, Objects: objects, Access: access}, Complete: command.CompleteUpload{Store: store, Objects: objects, Access: access, Clock: runtime.Clock}, Delete: command.DeleteFile{Store: store, Objects: objects, Access: access, Clock: runtime.Clock}, Get: query.GetFile{Store: store, Access: access}, List: query.ListFiles{Store: store, Access: access}, Download: query.DownloadFile{Store: store, Objects: objects, Access: access}, Receipt: query.GetReceipt{Store: store, Access: access}, Principals: runtime.Principals}
	return module.Unit{Name: Schema, Routes: h.Register, Requires: []string{"account.access.v1"}}
}
