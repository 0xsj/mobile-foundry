package local

import (
	"github.com/0xsj/mobile-foundry/backend/pkg/broadcast"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type Feed struct{ Hub *broadcast.Hub }

func (f Feed) Watch(actor, account id.ID) (<-chan struct{}, func(), error) {
	return f.Hub.Watch("notes."+account.String(), actor.String())
}
