package events

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const FilesConsumer = "notifications.files"

type Receiver interface {
	Handle(context.Context, app.Delivery) error
}

func SubscribeFiles(receiver Receiver) events.Subscription {
	return events.Subscription{Consumer: FilesConsumer, Names: []string{"files.upload_completed.v1"}, Handle: func(ctx context.Context, m events.Envelope) error {
		if m.Validate() != nil || m.Name != "files.upload_completed.v1" || m.Producer != "files" || !m.AccountID.Valid() || !m.ActorID.Valid() || m.Version != 2 {
			return events.ErrInvalid
		}
		var body struct {
			FileID    id.ID  `json:"file_id"`
			AccountID id.ID  `json:"account_id"`
			Version   uint64 `json:"version"`
		}
		if err := capability.Decode(m.Data, &body); err != nil {
			return err
		}
		if !body.FileID.Valid() || body.AccountID != m.AccountID || body.Version != m.Version {
			return events.ErrInvalid
		}
		return receiver.Handle(ctx, app.Delivery{EventID: m.ID, AccountID: m.AccountID, PrincipalID: m.ActorID, FileID: body.FileID, OccurredAt: m.OccurredAt})
	}}
}
