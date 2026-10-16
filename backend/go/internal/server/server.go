// Package server owns listener lifetime and process-level HTTP composition.
package server

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"sync/atomic"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/health"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

type Server struct {
	http            *http.Server
	units           *module.Assembly
	ready           atomic.Bool
	used            atomic.Bool
	shutdownTimeout time.Duration
}

// New takes ownership of units only on success. The caller closes them on failure.
func New(configuration config.Runtime, log *slog.Logger, units *module.Assembly) (*Server, error) {
	if log == nil || units == nil || configuration.ShutdownTimeout <= 0 {
		return nil, apperrors.New(apperrors.Invalid, "server.invalid", "invalid server dependencies")
	}
	server := &Server{units: units, shutdownTimeout: configuration.ShutdownTimeout}
	mux := http.NewServeMux()
	if err := units.Register(mux); err != nil {
		return nil, err
	}
	healthUnit, err := health.Build(func(ctx context.Context) error {
		if !server.ready.Load() {
			return apperrors.New(apperrors.Unavailable, "server.not_serving", "process is starting or draining")
		}
		return units.Ready(ctx)
	})
	if err != nil {
		return nil, err
	}
	healthRoutes := healthUnit.Routes
	healthUnit.Routes = func(mux *http.ServeMux) {
		healthRoutes(mux)
		mux.Handle("/", httpx.Methods(map[string]httpx.Endpoint{
			"GET":    func(http.ResponseWriter, *http.Request) error { return httpx.ErrNotFound },
			"POST":   func(http.ResponseWriter, *http.Request) error { return httpx.ErrNotFound },
			"PATCH":  func(http.ResponseWriter, *http.Request) error { return httpx.ErrNotFound },
			"PUT":    func(http.ResponseWriter, *http.Request) error { return httpx.ErrNotFound },
			"DELETE": func(http.ResponseWriter, *http.Request) error { return httpx.ErrNotFound },
		}))
	}
	healthAssembly, err := module.Assemble(module.Runtime{}, func(module.Runtime) (module.Unit, error) { return healthUnit, nil })
	if err != nil {
		return nil, err
	}
	if err := healthAssembly.Register(mux); err != nil {
		return nil, err
	}
	server.http = &http.Server{Addr: configuration.Address, Handler: httpx.Boundary(mux, id.Random{}, log), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second, WriteTimeout: 20 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16384}
	return server, nil
}

func (server *Server) Handler() http.Handler { return server.http.Handler }

// Serve owns listener and units, and runs once. Cancellation starts a graceful
// drain; active requests keep their context until they finish. On a drain timeout
// connections are closed and the caller receives failure; adapter cleanup is
// deliberately skipped rather than closing resources under running handlers.
func (server *Server) Serve(ctx context.Context, listener net.Listener) error {
	if listener == nil {
		return apperrors.New(apperrors.Invalid, "server.invalid_listener", "listener is required")
	}
	if !server.used.CompareAndSwap(false, true) {
		return apperrors.New(apperrors.Invalid, "server.already_served", "server can run only once")
	}
	if err := ctx.Err(); err != nil {
		return errors.Join(err, listener.Close(), server.units.Close())
	}
	server.ready.Store(true)
	done := make(chan error, 1)
	go func() { done <- server.http.Serve(listener) }()
	var serveError error
	serveFinished := false
	select {
	case err := <-done:
		serveError, serveFinished = err, true
	case <-ctx.Done():
	}
	server.ready.Store(false)
	shutdownContext, cancel := context.WithTimeout(context.WithoutCancel(ctx), server.shutdownTimeout)
	defer cancel()
	shutdownError := server.http.Shutdown(shutdownContext)
	if shutdownError != nil {
		return errors.Join(serveError, shutdownError, server.http.Close())
	}
	if !serveFinished {
		serveError = <-done
	}
	if errors.Is(serveError, http.ErrServerClosed) {
		serveError = nil
	}
	return errors.Join(serveError, server.units.Close())
}
