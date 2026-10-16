package server

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
)

func makeServer(t *testing.T, builders ...module.Builder) *Server {
	t.Helper()
	units, err := module.Assemble(module.Runtime{}, builders...)
	if err != nil {
		t.Fatal(err)
	}
	server, err := New(config.Runtime{ShutdownTimeout: 2 * time.Second}, slog.New(slog.NewTextHandler(io.Discard, nil)), units)
	if err != nil {
		t.Fatal(err)
	}
	return server
}

func TestHealthHTTPContractAndStartupReadiness(t *testing.T) {
	server := makeServer(t)
	for _, test := range []struct {
		method, path string
		status       int
	}{{"GET", "/v1/health/live", 200}, {"HEAD", "/v1/health/live", 200}, {"GET", "/v1/health/ready", 503}, {"POST", "/v1/health/live", 405}, {"GET", "/v1/missing", 404}} {
		w := httptest.NewRecorder()
		r := httptest.NewRequest(test.method, test.path, nil)
		server.Handler().ServeHTTP(w, r)
		if w.Code != test.status {
			t.Fatalf("%s %s = %d %s", test.method, test.path, w.Code, w.Body.String())
		}
		if w.Header().Get("Cache-Control") != "no-store" || w.Header().Get("X-Request-ID") == "" {
			t.Fatal("missing response metadata")
		}
		if test.method == "HEAD" && w.Body.Len() != 0 {
			t.Fatal("HEAD emitted a body")
		}
		if test.status >= 400 && !strings.HasPrefix(w.Header().Get("Content-Type"), "application/problem+json") {
			t.Fatal("failure was not a problem")
		}
	}
}

func TestConflictingProcessRoutesFailStartup(t *testing.T) {
	for _, path := range []string{"/", "/v1/health/live"} {
		units, err := module.Assemble(module.Runtime{}, func(module.Runtime) (module.Unit, error) {
			return module.Unit{Name: "conflict", Routes: func(mux *http.ServeMux) { mux.HandleFunc(path, func(http.ResponseWriter, *http.Request) {}) }}, nil
		})
		if err != nil {
			t.Fatal(err)
		}
		_, err = New(config.Runtime{ShutdownTimeout: time.Second}, slog.New(slog.NewTextHandler(io.Discard, nil)), units)
		if err == nil {
			t.Fatalf("process route conflict %s did not fail startup", path)
		}
		_ = units.Close()
	}
}

func TestLiveListenerReadinessAndGracefulDrain(t *testing.T) {
	entered, release := make(chan struct{}), make(chan struct{})
	var closed atomic.Bool
	server := makeServer(t, func(module.Runtime) (module.Unit, error) {
		return module.Unit{Name: "fixture", Ready: func(context.Context) error { return nil }, Routes: func(mux *http.ServeMux) {
			mux.Handle("/work", httpx.Methods(map[string]httpx.Endpoint{"GET": func(w http.ResponseWriter, r *http.Request) error {
				close(entered)
				<-release
				if closed.Load() {
					t.Error("module resources closed under active work")
				}
				return httpx.WriteJSON(w, r, 200, map[string]string{"status": "done"})
			}}))
		}, Close: func() error { closed.Store(true); return nil }}, nil
	})
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	done := make(chan error, 1)
	go func() { done <- server.Serve(ctx, listener) }()
	client := &http.Client{Timeout: 3 * time.Second}
	base := "http://" + listener.Addr().String()
	response, err := client.Get(base + "/v1/health/ready")
	if err != nil {
		t.Fatal(err)
	}
	_ = response.Body.Close()
	if response.StatusCode != 200 {
		t.Fatalf("serving process was not ready: %d", response.StatusCode)
	}
	work := make(chan error, 1)
	go func() {
		response, err := client.Get(base + "/work")
		if err == nil {
			_, err = io.Copy(io.Discard, response.Body)
			_ = response.Body.Close()
			if response.StatusCode != 200 {
				err = errors.New("work failed")
			}
		}
		work <- err
	}()
	select {
	case <-entered:
	case <-time.After(3 * time.Second):
		t.Fatal("request did not enter")
	}
	cancel()
	deadline := time.Now().Add(time.Second)
	for server.ready.Load() && time.Now().Before(deadline) {
		time.Sleep(time.Millisecond)
	}
	if server.ready.Load() {
		t.Fatal("drain did not clear readiness")
	}
	w := httptest.NewRecorder()
	server.Handler().ServeHTTP(w, httptest.NewRequest("GET", "/v1/health/ready", nil))
	if w.Code != 503 || closed.Load() {
		t.Fatal("draining process ready or closed too early")
	}
	close(release)
	if err := <-work; err != nil {
		t.Fatal(err)
	}
	select {
	case err := <-done:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("graceful drain did not finish")
	}
	if !closed.Load() {
		t.Fatal("module resources not closed after drain")
	}
}

func TestFailedReadinessAndForcedDrain(t *testing.T) {
	entered := make(chan struct{})
	var closed atomic.Bool
	server := makeServer(t, func(module.Runtime) (module.Unit, error) {
		return module.Unit{Name: "fixture", Ready: func(context.Context) error { return errors.New("private failure") }, Routes: func(mux *http.ServeMux) {
			mux.HandleFunc("/work", func(w http.ResponseWriter, r *http.Request) { close(entered); <-r.Context().Done() })
		}, Close: func() error { closed.Store(true); return nil }}, nil
	})
	server.shutdownTimeout = 25 * time.Millisecond
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	done := make(chan error, 1)
	go func() { done <- server.Serve(ctx, listener) }()
	client := &http.Client{Timeout: 2 * time.Second}
	base := "http://" + listener.Addr().String()
	response, err := client.Get(base + "/v1/health/ready")
	if err != nil {
		t.Fatal(err)
	}
	body, _ := io.ReadAll(response.Body)
	_ = response.Body.Close()
	if response.StatusCode != 503 || strings.Contains(string(body), "private failure") {
		t.Fatal("probe failure not safely projected")
	}
	requestDone := make(chan struct{})
	go func() {
		response, err := client.Get(base + "/work")
		if err == nil {
			_ = response.Body.Close()
		}
		close(requestDone)
	}()
	select {
	case <-entered:
	case <-time.After(2 * time.Second):
		t.Fatal("request did not enter")
	}
	cancel()
	select {
	case err := <-done:
		if !errors.Is(err, context.DeadlineExceeded) {
			t.Fatalf("expected drain deadline, got %v", err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("forced shutdown stalled")
	}
	<-requestDone
	if closed.Load() {
		t.Fatal("forced drain closed adapters under potentially active handlers")
	}
	_ = server.units.Close() // test fixture has no remaining work now.
}
