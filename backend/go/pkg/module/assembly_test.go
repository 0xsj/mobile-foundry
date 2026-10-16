package module

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"net/http"
	"reflect"
	"testing"
)

func TestCleanupOnPartialFailure(t *testing.T) {
	var order []string
	failure := errors.New("build failed")
	build := func(name string) Builder {
		return func(Runtime) (Unit, error) {
			return Unit{Name: name, Routes: func(*http.ServeMux) {}, Close: func() error { order = append(order, name); return nil }}, nil
		}
	}
	if _, err := Assemble(Runtime{}, build("first"), build("second"), func(Runtime) (Unit, error) { return Unit{}, failure }); !errors.Is(err, failure) {
		t.Fatal("build failure lost")
	}
	if !reflect.DeepEqual(order, []string{"second", "first"}) {
		t.Fatalf("cleanup order: %v", order)
	}
	order = nil
	assembly, err := Assemble(Runtime{}, build("first"), build("second"))
	if err != nil {
		t.Fatal(err)
	}
	_ = assembly.Close()
	_ = assembly.Close()
	if !reflect.DeepEqual(order, []string{"second", "first"}) {
		t.Fatal("cleanup was repeated")
	}
}

func TestStopAllWorkersBeforeClosingAnyResources(t *testing.T) {
	var order []string
	build := func(name string) Builder {
		return func(Runtime) (Unit, error) {
			return Unit{Name: name, Routes: func(*http.ServeMux) {}, Stop: func() error { order = append(order, "stop "+name); return nil }, Close: func() error { order = append(order, "close "+name); return nil }}, nil
		}
	}
	assembly, err := Assemble(Runtime{}, build("account"), build("audit"))
	if err != nil {
		t.Fatal(err)
	}
	_ = assembly.Close()
	_ = assembly.Close()
	if !reflect.DeepEqual(order, []string{"stop audit", "stop account", "close audit", "close account"}) {
		t.Fatal(order)
	}
}

func TestRequirementsResolveBeforeWorkersStart(t *testing.T) {
	started, closed := false, false
	consumer := func(Runtime) (Unit, error) {
		return Unit{Name: "audit", Routes: func(*http.ServeMux) {}, Requires: []string{"account.access.v1"}, Start: func() error { started = true; return nil }, Close: func() error { closed = true; return nil }}, nil
	}
	if _, err := Assemble(Runtime{Capabilities: capability.NewRegistry()}, consumer); err == nil || started || !closed {
		t.Fatal("unresolved consumer started")
	}
	provider := func(Runtime) (Unit, error) {
		return Unit{Name: "account", Routes: func(*http.ServeMux) {}, Provides: []capability.Provision{{Name: "account.access.v1", Callers: []string{"audit"}, Handler: func(context.Context, json.RawMessage) (any, error) { return struct{}{}, nil }}}}, nil
	}
	assembly, err := Assemble(Runtime{Capabilities: capability.NewRegistry()}, consumer, provider)
	if err != nil || !started {
		t.Fatal("provider order became a dependency", err)
	}
	_ = assembly.Close()
}

func TestRegistrationAndReadiness(t *testing.T) {
	closed := 0
	build := func(Runtime) (Unit, error) {
		return Unit{Name: "same", Routes: func(*http.ServeMux) {}, Close: func() error { closed++; return nil }}, nil
	}
	if _, err := Assemble(Runtime{}, build, build); err == nil || closed != 2 {
		t.Fatal("duplicate module ownership not cleaned up")
	}
	if _, err := Assemble(Runtime{}, nil); err == nil {
		t.Fatal("nil builder accepted")
	}
	failure := errors.New("probe failed")
	assembly, err := Assemble(Runtime{}, func(Runtime) (Unit, error) {
		return Unit{Name: "probe", Routes: func(mux *http.ServeMux) {
			mux.HandleFunc("/same", func(http.ResponseWriter, *http.Request) {})
			mux.HandleFunc("/same", func(http.ResponseWriter, *http.Request) {})
		}, Ready: func(context.Context) error { return failure }}, nil
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := assembly.Register(http.NewServeMux()); err == nil {
		t.Fatal("route conflict did not fail startup")
	}
	if err := assembly.Ready(context.Background()); !errors.Is(err, failure) {
		t.Fatal("readiness cause lost")
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if !errors.Is(assembly.Ready(ctx), context.Canceled) {
		t.Fatal("readiness ignored cancellation")
	}
}
