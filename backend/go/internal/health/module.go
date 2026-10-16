// Package health is a small runtime composition exemplar, not a business aggregate.
package health

import (
	"github.com/0xsj/mobile-foundry/backend/internal/health/app/query"
	v1 "github.com/0xsj/mobile-foundry/backend/internal/health/transport/http/v1"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

type Dependencies struct{ Readiness query.Readiness }

func NewModule(dependencies Dependencies) (module.Unit, error) {
	ready, err := query.NewCheckReady(dependencies.Readiness)
	if err != nil {
		return module.Unit{}, err
	}
	routes := v1.New(ready)
	return module.Unit{Name: "health", Routes: routes.Routes}, nil
}
