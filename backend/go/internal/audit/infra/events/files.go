package events

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"strings"
)

const FilesConsumer = "audit.files"

func SubscribeFiles(recorder Recorder) events.Subscription {
	return events.Subscription{Consumer: FilesConsumer, Names: []string{"files.upload_requested.v1", "files.upload_completed.v1", "files.deleted.v1"}, Handle: func(ctx context.Context, m events.Envelope) error {
		if m.Validate() != nil || m.Producer != "files" || !m.AccountID.Valid() {
			return events.ErrInvalid
		}
		var b struct {
			FileID    id.ID  `json:"file_id"`
			AccountID id.ID  `json:"account_id"`
			Version   uint64 `json:"version"`
		}
		if err := capability.Decode(m.Data, &b); err != nil {
			return err
		}
		if !b.FileID.Valid() || b.AccountID != m.AccountID || b.Version != m.Version {
			return events.ErrInvalid
		}
		action := strings.TrimSuffix(m.Name, ".v1")
		switch action {
		case "files.upload_requested", "files.upload_completed", "files.deleted":
		default:
			return events.ErrInvalid
		}
		return recorder.Handle(ctx, FilesConsumer, domain.Evidence{EventID: m.ID, AccountID: m.AccountID, ActorID: m.ActorID, FileID: b.FileID, Action: action, Version: m.Version, OccurredAt: m.OccurredAt, RequestID: m.Metadata.RequestID, CorrelationID: m.Metadata.CorrelationID})
	}}
}
