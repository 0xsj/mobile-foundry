package notifications

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/push"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
)

// LoadPushGateway is a composition facade; cmd never imports module internals.
func LoadPushGateway(path string, clock clock.Clock) (app.PushGateway, func() error, error) {
	if path == "" {
		return nil, func() error { return nil }, nil
	}
	gateway, err := push.Load(path, clock)
	if err != nil {
		return nil, nil, err
	}
	return gateway, gateway.Close, nil
}
