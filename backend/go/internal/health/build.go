package health

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/health/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

// Build adapts process readiness into the application's port. A health query
// never probes itself recursively. The process owns listener/drain state.
func Build(ready func(context.Context) error) (module.Unit, error) {
	if ready == nil {
		return NewModule(Dependencies{})
	}
	return NewModule(Dependencies{Readiness: query.ProbeFunc(ready)})
}
