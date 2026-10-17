package notes

import (
	"github.com/0xsj/mobile-foundry/backend/pkg/broadcast"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type signals struct{ hub *broadcast.Hub }

func (s signals) Changed(account id.ID) {
	if s.hub != nil {
		s.hub.Publish("notes." + account.String())
	}
}
