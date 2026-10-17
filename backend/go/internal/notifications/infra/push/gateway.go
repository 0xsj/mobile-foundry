// Package push implements opt-in outbound preview protocols. It never registers
// installations, selects recipients, retries sends or logs provider content.
package push

import (
	"bytes"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

var (
	ErrConfig      = apperrors.New(apperrors.Invalid, "push.configuration", "invalid push provider configuration").WithPublic("Check the private push provider configuration.")
	ErrRoute       = apperrors.New(apperrors.Invalid, "push.route_not_selected", "push route or provider token encoding not selected").WithPublic("This push route or token encoding is not supported by the configured sender.")
	ErrUnavailable = apperrors.New(apperrors.Unavailable, "push.provider_unavailable", "push provider request not confirmed").WithPublic("The provider did not confirm this request. Refresh registration before another attempt; sending again may duplicate a notification.")
	ErrCredentials = apperrors.New(apperrors.Unavailable, "push.provider_credentials", "push provider refused credentials or routing configuration").WithPublic("Check the server's push provider credentials and route.")
	ErrLimited     = apperrors.New(apperrors.RateLimited, "push.provider_limited", "push provider throttled the request").WithPublic("The provider limited this request. No automatic retry was made.")
)

type routeConfig struct {
	Provider           string `json:"provider"`
	Application        string `json:"application"`
	Environment        string `json:"environment"`
	TeamID             string `json:"team_id,omitempty"`
	KeyID              string `json:"key_id,omitempty"`
	KeyFile            string `json:"key_file,omitempty"`
	ProjectID          string `json:"project_id,omitempty"`
	ServiceAccountFile string `json:"service_account_file,omitempty"`
}
type configuration struct {
	Routes []routeConfig `json:"routes"`
}

type Gateway struct {
	routes    map[domain.PushRoute]app.PushGateway
	transport *http.Transport
}

func (*Gateway) String() string   { return "push.Gateway{redacted}" }
func (*Gateway) GoString() string { return "push.Gateway{redacted}" }
func (g *Gateway) Close() error   { g.transport.CloseIdleConnections(); return nil }

// Load reads bounded files, resolves credential paths relative to this config,
// and makes no network request. Endpoints are fixed; redirects are refused.
func Load(path string, clock clock.Clock) (*Gateway, error) {
	if clock == nil {
		return nil, ErrConfig
	}
	wire, err := readFile(path, 65536)
	if err != nil {
		return nil, ErrConfig
	}
	var config configuration
	if decode(wire, &config) != nil || len(config.Routes) < 1 || len(config.Routes) > 16 {
		return nil, ErrConfig
	}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.ForceAttemptHTTP2 = true
	transport.TLSClientConfig = &tls.Config{MinVersion: tls.VersionTLS12}
	client := clientFor(transport)
	g := &Gateway{routes: map[domain.PushRoute]app.PushGateway{}, transport: transport}
	for _, r := range config.Routes {
		route := domain.PushRoute{Provider: r.Provider, Application: r.Application, Environment: r.Environment}
		if route.Validate() != nil || g.routes[route] != nil {
			return nil, ErrConfig
		}
		resolve := func(name string) string {
			if filepath.IsAbs(name) {
				return name
			}
			return filepath.Join(filepath.Dir(path), name)
		}
		var sender app.PushGateway
		switch route.Provider {
		case "apns":
			if r.ProjectID != "" || r.ServiceAccountFile != "" || r.KeyFile == "" {
				return nil, ErrConfig
			}
			key, err := readKey(resolve(r.KeyFile))
			if err != nil {
				return nil, ErrConfig
			}
			sender, err = newAPNs(route, r.TeamID, r.KeyID, key, clock, client)
			if err != nil {
				return nil, err
			}
		case "fcm":
			if r.TeamID != "" || r.KeyID != "" || r.KeyFile != "" || r.ServiceAccountFile == "" {
				return nil, ErrConfig
			}
			wire, err := readFile(resolve(r.ServiceAccountFile), 32768)
			if err != nil {
				return nil, ErrConfig
			}
			sender, err = newFCM(route, r.ProjectID, wire, clock, client)
			if err != nil {
				return nil, err
			}
		}
		g.routes[route] = sender
	}
	return g, nil
}

func clientFor(transport *http.Transport) *http.Client {
	return &http.Client{Transport: transport, Timeout: 5 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
}

func (g *Gateway) SendPreview(ctx context.Context, address app.PushAddress, preview id.ID) (app.PushDisposition, error) {
	if err := ctx.Err(); err != nil {
		return "", err
	}
	if address.Lease.Validate() != nil || address.Spec.Validate() != nil || !preview.Valid() || address.RegisteredAt.IsZero() {
		return "", ErrRoute
	}
	sender := g.routes[address.Spec.Route]
	if sender == nil {
		return "", ErrRoute
	}
	return sender.SendPreview(ctx, address, preview)
}

func readFile(path string, limit int64) ([]byte, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, ErrConfig
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil || !info.Mode().IsRegular() {
		return nil, ErrConfig
	}
	wire, err := io.ReadAll(io.LimitReader(f, limit+1))
	if err != nil || int64(len(wire)) > limit {
		return nil, ErrConfig
	}
	return wire, nil
}
func readKey(path string) (any, error) {
	wire, err := readFile(path, 16384)
	if err != nil {
		return nil, err
	}
	return parseKey(wire)
}
func parseKey(wire []byte) (any, error) {
	block, rest := pem.Decode(wire)
	if block == nil || block.Type != "PRIVATE KEY" || len(bytes.TrimSpace(rest)) != 0 {
		return nil, ErrConfig
	}
	key, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, ErrConfig
	}
	return key, nil
}
func decode(wire []byte, target any) error {
	d := json.NewDecoder(bytes.NewReader(wire))
	d.DisallowUnknownFields()
	if err := d.Decode(target); err != nil {
		return err
	}
	if err := d.Decode(new(any)); err != io.EOF {
		return ErrConfig
	}
	return nil
}
func response(client *http.Client, request *http.Request) (int, []byte, error) {
	r, err := client.Do(request)
	if err != nil {
		if request.Context().Err() != nil {
			return 0, nil, request.Context().Err()
		}
		return 0, nil, ErrUnavailable
	}
	defer r.Body.Close()
	wire, err := io.ReadAll(io.LimitReader(r.Body, 16385))
	if err != nil || len(wire) > 16384 {
		if request.Context().Err() != nil {
			return 0, nil, request.Context().Err()
		}
		return 0, nil, ErrUnavailable
	}
	return r.StatusCode, wire, nil
}
func providerError(status int) error {
	switch status {
	case 401, 403:
		return ErrCredentials
	case 429:
		return ErrLimited
	default:
		return ErrUnavailable
	}
}
func previewData(preview id.ID) map[string]string {
	return map[string]string{"version": "1", "kind": "push_preview", "preview_id": preview.String()}
}

const previewTitle = "Mobile push preview"
const previewBody = "Open the app to continue."
