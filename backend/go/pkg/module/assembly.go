package module

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"regexp"
	"strings"
	"sync"

	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
)

var namePattern = regexp.MustCompile(`^[a-z][a-z0-9_]{0,63}$`)

// Assembly owns selected units. It neither selects providers nor builds a
// service locator. Builders choose their own application ports and adapters.
type Assembly struct {
	units      []Unit
	closeOnce  sync.Once
	closeError error
}

func Assemble(runtime Runtime, builders ...Builder) (*Assembly, error) {
	assembly := &Assembly{}
	names := make(map[string]bool)
	for _, build := range builders {
		if build == nil {
			return nil, errors.Join(invalid("nil module builder"), assembly.Close())
		}
		unit, err := build(runtime)
		if err != nil {
			return nil, errors.Join(err, assembly.Close())
		}
		// A successful builder transfers ownership, including when its unit is invalid.
		assembly.units = append(assembly.units, unit)
		if !namePattern.MatchString(unit.Name) || names[unit.Name] || unit.Routes == nil {
			return nil, errors.Join(invalid("invalid or duplicate module registration"), assembly.Close())
		}
		names[unit.Name] = true
	}
	// Register every provider before resolving requirements or starting workers.
	for _, unit := range assembly.units {
		for _, provider := range unit.Provides {
			if !strings.HasPrefix(provider.Name, unit.Name+".") {
				return nil, errors.Join(invalid("capability provider must belong to its module"), assembly.Close())
			}
			if runtime.Capabilities == nil {
				return nil, errors.Join(invalid("capability registry is required"), assembly.Close())
			}
			if err := runtime.Capabilities.Register(provider); err != nil {
				return nil, errors.Join(err, assembly.Close())
			}
		}
		for _, subscription := range unit.Subscriptions {
			if !strings.HasPrefix(subscription.Consumer, unit.Name+".") {
				return nil, errors.Join(invalid("event consumer must belong to its module"), assembly.Close())
			}
			if runtime.Events == nil {
				return nil, errors.Join(invalid("event bus is required"), assembly.Close())
			}
			if err := runtime.Events.Register(subscription); err != nil {
				return nil, errors.Join(err, assembly.Close())
			}
		}
	}
	for _, unit := range assembly.units {
		for _, requirement := range unit.Requires {
			if !capability.ValidName(requirement) {
				return nil, errors.Join(invalid("invalid capability requirement"), assembly.Close())
			}
			if runtime.Capabilities == nil || !runtime.Capabilities.Provides(requirement) {
				return nil, errors.Join(invalid("unresolved required capability"), assembly.Close())
			}
		}
	}
	if runtime.Capabilities != nil {
		runtime.Capabilities.Freeze()
	}
	if runtime.Events != nil {
		runtime.Events.Freeze()
	}
	for _, unit := range assembly.units {
		if unit.Start != nil {
			if err := unit.Start(); err != nil {
				return nil, errors.Join(err, assembly.Close())
			}
		}
	}
	return assembly, nil
}

// Register converts ServeMux registration panics into startup failure. A failed
// mux must be discarded; partial routes must never be served.
func (assembly *Assembly) Register(mux *http.ServeMux) (err error) {
	if mux == nil {
		return invalid("route mux is required")
	}
	defer func() {
		if value := recover(); value != nil {
			err = invalid("route registration failed")
		}
	}()
	for _, unit := range assembly.units {
		unit.Routes(mux)
	}
	return nil
}

func (assembly *Assembly) Ready(ctx context.Context) error {
	for _, unit := range assembly.units {
		if err := ctx.Err(); err != nil {
			return err
		}
		if unit.Ready != nil {
			if err := unit.Ready(ctx); err != nil {
				return fmt.Errorf("module %s is not ready: %w", unit.Name, err)
			}
		}
	}
	return ctx.Err()
}

// Close stops every producer before resource cleanup, in reverse build order. A builder that
// returns an error remains responsible for its own partially built resources.
func (assembly *Assembly) Close() error {
	assembly.closeOnce.Do(func() {
		for index := len(assembly.units) - 1; index >= 0; index-- {
			if stop := assembly.units[index].Stop; stop != nil {
				assembly.closeError = errors.Join(assembly.closeError, stop())
			}
		}
		for index := len(assembly.units) - 1; index >= 0; index-- {
			if close := assembly.units[index].Close; close != nil {
				assembly.closeError = errors.Join(assembly.closeError, close())
			}
		}
	})
	return assembly.closeError
}
func invalid(message string) error {
	return apperrors.New(apperrors.Invalid, "module.invalid", message)
}
