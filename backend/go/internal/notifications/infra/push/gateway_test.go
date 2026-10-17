package push

import (
	"context"
	"crypto"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type testClock struct{ now time.Time }

func (c *testClock) Now() time.Time { return c.now }
func testID(t *testing.T) id.ID {
	t.Helper()
	v, err := (id.Random{}).NewID()
	if err != nil {
		t.Fatal(err)
	}
	return v
}
func testAddress(t *testing.T, provider string) app.PushAddress {
	t.Helper()
	environment, token := "production", "opaque:registration-address"
	if provider == "apns" {
		environment = "sandbox"
		token = "000102aaff"
	}
	return app.PushAddress{Lease: app.PushLease{InstallationID: testID(t), PrincipalID: testID(t), SessionID: testID(t), Version: 1}, Spec: domain.InstallationSpec{Route: domain.PushRoute{Provider: provider, Application: "dev.example.app", Environment: environment}, Token: token}, RegisteredAt: time.Date(2026, 10, 17, 0, 0, 0, 0, time.UTC)}
}
func testPEM(t *testing.T, key any) []byte {
	t.Helper()
	v, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	return pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: v})
}
func jwtParts(t *testing.T, value string) (map[string]any, map[string]any, []byte, []byte) {
	t.Helper()
	parts := strings.Split(value, ".")
	if len(parts) != 3 {
		t.Fatal("JWT parts")
	}
	decode := func(s string) []byte {
		v, err := base64.RawURLEncoding.DecodeString(s)
		if err != nil {
			t.Fatal("JWT encoding")
		}
		return v
	}
	var header, claims map[string]any
	if json.Unmarshal(decode(parts[0]), &header) != nil || json.Unmarshal(decode(parts[1]), &claims) != nil {
		t.Fatal("JWT JSON")
	}
	return header, claims, []byte(parts[0] + "." + parts[1]), decode(parts[2])
}
func tlsServer(handler http.HandlerFunc) *httptest.Server {
	s := httptest.NewUnstartedServer(handler)
	s.EnableHTTP2 = true
	s.StartTLS()
	return s
}

func TestAPNsProtocolAndDefinitiveRejections(t *testing.T) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	address := testAddress(t, "apns")
	preview := testID(t)
	clock := &testClock{address.RegisteredAt.Add(time.Minute)}
	status, body, calls := 200, "", 0
	server := tlsServer(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if r.ProtoMajor != 2 || r.Method != "POST" || r.URL.Path != "/3/device/"+address.Spec.Token {
			t.Error("APNs protocol/path")
		}
		for k, v := range map[string]string{"apns-topic": address.Spec.Route.Application, "apns-push-type": "alert", "apns-priority": "10", "apns-expiration": "0", "apns-id": preview.String()} {
			if r.Header.Get(k) != v {
				t.Error("APNs header", k)
			}
		}
		header, claims, input, signature := jwtParts(t, strings.TrimPrefix(r.Header.Get("authorization"), "bearer "))
		digest := sha256.Sum256(input)
		if header["alg"] != "ES256" || header["kid"] != "KEYID00001" || claims["iss"] != "TEAMID0001" || len(signature) != 64 || !ecdsa.Verify(&key.PublicKey, digest[:], new(big.Int).SetBytes(signature[:32]), new(big.Int).SetBytes(signature[32:])) {
			t.Error("APNs signature/claims")
		}
		var payload map[string]any
		if json.NewDecoder(r.Body).Decode(&payload) != nil || payload["kind"] != "push_preview" || payload["version"] != "1" || payload["preview_id"] != preview.String() || len(payload) != 4 {
			t.Error("APNs preview payload")
		}
		w.WriteHeader(status)
		_, _ = io.WriteString(w, body)
	})
	defer server.Close()
	sender, err := newAPNs(address.Spec.Route, "TEAMID0001", "KEYID00001", key, clock, clientFor(server.Client().Transport.(*http.Transport)))
	if err != nil {
		t.Fatal(err)
	}
	sender.endpoint = server.URL
	for _, row := range []struct {
		name              string
		status            int
		body              string
		invalid, accepted bool
	}{
		{"accepted", 200, "", false, true},
		{"bad_device", 400, `{"reason":"BadDeviceToken"}`, true, false},
		{"unregistered", 410, fmt.Sprintf(`{"reason":"Unregistered","timestamp":%d}`, clock.now.UnixMilli()), true, false},
		{"old_unregistered", 410, fmt.Sprintf(`{"reason":"Unregistered","timestamp":%d}`, address.RegisteredAt.Add(-time.Second).UnixMilli()), false, false},
		{"missing_timestamp", 410, `{"reason":"Unregistered"}`, false, false},
		{"future_timestamp", 410, `{"reason":"Unregistered","timestamp":9223372036854775807}`, false, false},
		{"topic_mismatch", 400, `{"reason":"DeviceTokenNotForTopic"}`, false, false},
		{"auth", 403, `{"reason":"ExpiredProviderToken"}`, false, false},
		{"limited", 429, `{"reason":"TooManyRequests"}`, false, false},
		{"server", 503, `{"reason":"Shutdown"}`, false, false},
		{"malformed", 400, "private unexpected body", false, false},
		{"unexpected_success_body", 200, `{}`, false, false},
	} {
		t.Run(row.name, func(t *testing.T) {
			status, body = row.status, row.body
			before := calls
			result, err := sender.SendPreview(context.Background(), address, preview)
			if calls != before+1 {
				t.Fatal("provider retry")
			}
			if row.accepted {
				if err != nil || result != app.PushAccepted {
					t.Fatal("acceptance", err)
				}
			} else if row.invalid {
				if err != nil || result != app.PushInvalidTarget {
					t.Fatal("invalid address", err)
				}
			} else if err == nil || result == app.PushInvalidTarget {
				t.Fatal("ambiguous error erased target")
			}
		})
	}
	first, _ := sender.authorization()
	second, _ := sender.authorization()
	if first != second {
		t.Fatal("APNs auth not cached")
	}
	clock.now = clock.now.Add(51 * time.Minute)
	third, _ := sender.authorization()
	if third == first {
		t.Fatal("APNs auth not refreshed")
	}
	before := calls
	address.Spec.Token = "UPPERCASE"
	if _, err := sender.SendPreview(context.Background(), address, preview); !errors.Is(err, ErrRoute) || calls != before {
		t.Fatal("invalid token sent", err)
	}
}

func TestFCMProtocolOAuthAndErrorEvidence(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	address := testAddress(t, "fcm")
	preview := testID(t)
	clock := &testClock{address.RegisteredAt.Add(time.Minute)}
	credentials, _ := json.Marshal(serviceAccount{Type: "service_account", ProjectID: "test-project", PrivateKeyID: "test-key", PrivateKey: string(testPEM(t, key)), ClientEmail: "sender@test-project.iam.gserviceaccount.com", TokenURI: oauthEndpoint})
	var oauthCalls, sendCalls atomic.Int64
	status, body := 200, `{"name":"projects/test-project/messages/accepted"}`
	server := tlsServer(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/token" {
			oauthCalls.Add(1)
			if r.ParseForm() != nil || r.Form.Get("grant_type") != "urn:ietf:params:oauth:grant-type:jwt-bearer" {
				t.Error("OAuth grant")
			}
			header, claims, input, signature := jwtParts(t, r.Form.Get("assertion"))
			digest := sha256.Sum256(input)
			if header["alg"] != "RS256" || claims["aud"] != oauthEndpoint || claims["scope"] != "https://www.googleapis.com/auth/firebase.messaging" || claims["iss"] != "sender@test-project.iam.gserviceaccount.com" || rsa.VerifyPKCS1v15(&key.PublicKey, crypto.SHA256, digest[:], signature) != nil {
				t.Error("OAuth assertion")
			}
			_, _ = io.WriteString(w, `{"access_token":"private-oauth-access","token_type":"Bearer","expires_in":3600}`)
			return
		}
		sendCalls.Add(1)
		if r.URL.Path != "/v1/projects/test-project/messages:send" || r.Header.Get("authorization") != "Bearer private-oauth-access" || r.Method != "POST" {
			t.Error("FCM request")
		}
		var payload struct {
			Message struct {
				Token   string            `json:"token"`
				Data    map[string]string `json:"data"`
				Android struct {
					TTL     string `json:"ttl"`
					Package string `json:"restricted_package_name"`
				} `json:"android"`
			} `json:"message"`
		}
		if json.NewDecoder(r.Body).Decode(&payload) != nil || payload.Message.Token != address.Spec.Token || payload.Message.Data["kind"] != "push_preview" || payload.Message.Data["preview_id"] != preview.String() || payload.Message.Android.TTL != "0s" || payload.Message.Android.Package != address.Spec.Route.Application {
			t.Error("FCM payload")
		}
		w.WriteHeader(status)
		_, _ = io.WriteString(w, body)
	})
	defer server.Close()
	sender, err := newFCM(address.Spec.Route, "test-project", credentials, clock, clientFor(server.Client().Transport.(*http.Transport)))
	if err != nil {
		t.Fatal(err)
	}
	sender.endpoint = server.URL + "/v1/projects/test-project/messages:send"
	sender.tokenURL = server.URL + "/token"
	for _, row := range []struct {
		name              string
		status            int
		body              string
		invalid, accepted bool
	}{
		{"accepted", 200, `{"name":"projects/test-project/messages/accepted"}`, false, true},
		{"unregistered", 404, `{"error":{"code":404,"status":"NOT_FOUND","details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}`, true, false},
		{"generic_not_found", 404, `{"error":{"code":404,"status":"NOT_FOUND"}}`, false, false},
		{"invalid_argument", 400, `{"error":{"code":400,"status":"INVALID_ARGUMENT"}}`, false, false},
		{"project_mismatch", 403, `{"error":{"status":"PERMISSION_DENIED"}}`, false, false},
		{"limited", 429, `{}`, false, false},
		{"server", 500, `{}`, false, false},
		{"missing_name", 200, `{}`, false, false},
		{"foreign_name", 200, `{"name":"projects/another/messages/id"}`, false, false},
		{"oversize", 200, strings.Repeat("x", 17000), false, false},
	} {
		t.Run(row.name, func(t *testing.T) {
			status, body = row.status, row.body
			before := sendCalls.Load()
			result, err := sender.SendPreview(context.Background(), address, preview)
			if sendCalls.Load() != before+1 {
				t.Fatal("FCM retry")
			}
			if row.accepted {
				if err != nil || result != app.PushAccepted {
					t.Fatal("acceptance", err)
				}
			} else if row.invalid {
				if err != nil || result != app.PushInvalidTarget {
					t.Fatal("invalid address", err)
				}
			} else if err == nil || result == app.PushInvalidTarget {
				t.Fatal("ambiguous error erased target")
			}
		})
	}
	if oauthCalls.Load() != 1 {
		t.Fatal("OAuth cache missed")
	}
	status, body = 200, `{"name":"projects/test-project/messages/concurrent"}`
	sender.cached = ""
	var group sync.WaitGroup
	for range 8 {
		group.Go(func() {
			if _, err := sender.SendPreview(context.Background(), address, preview); err != nil {
				t.Error(err)
			}
		})
	}
	group.Wait()
	if oauthCalls.Load() != 2 {
		t.Fatal("concurrent OAuth requests not coalesced")
	}
	clock.now = clock.now.Add(time.Hour)
	if _, err := sender.SendPreview(context.Background(), address, preview); err != nil || oauthCalls.Load() != 3 {
		t.Fatal("OAuth expiry", err)
	}
	if output := fmt.Sprintf("%v %#v", sender, sender); strings.Contains(output, "private-oauth-access") || strings.Contains(output, "BEGIN PRIVATE KEY") {
		t.Fatal("credentials printed")
	}
}

func TestConfigAdmissionRoutesAndRedirectPolicy(t *testing.T) {
	dir := t.TempDir()
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err := os.WriteFile(filepath.Join(dir, "signing.p8"), testPEM(t, key), 0600); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, "push.local.json")
	route := routeConfig{Provider: "apns", Application: "dev.example.app", Environment: "sandbox", TeamID: "TEAMID0001", KeyID: "KEYID00001", KeyFile: "signing.p8"}
	write := func(value any) {
		wire, _ := json.Marshal(value)
		if err := os.WriteFile(path, wire, 0600); err != nil {
			t.Fatal(err)
		}
	}
	clock := &testClock{time.Now()}
	write(configuration{Routes: []routeConfig{route}})
	gateway, err := Load(path, clock)
	if err != nil {
		t.Fatal(err)
	}
	defer gateway.Close()
	var redirected atomic.Int64
	other := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { redirected.Add(1) }))
	defer other.Close()
	server := tlsServer(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Location", other.URL)
		w.WriteHeader(307)
	})
	defer server.Close()
	sender := gateway.routes[domain.PushRoute{Provider: "apns", Application: route.Application, Environment: route.Environment}].(*apnsSender)
	// Keep the production client's redirect policy; replace only its test TLS trust/endpoint.
	sender.client.Transport = server.Client().Transport
	sender.endpoint = server.URL
	if _, err := gateway.SendPreview(context.Background(), testAddress(t, "apns"), testID(t)); err == nil || redirected.Load() != 0 {
		t.Fatal("redirect followed")
	}
	a := testAddress(t, "fcm")
	if _, err := gateway.SendPreview(context.Background(), a, testID(t)); !errors.Is(err, ErrRoute) {
		t.Fatal("route not allowlisted", err)
	}
	for _, config := range []any{map[string]any{"routes": []routeConfig{route}, "endpoint": "https://other"}, configuration{}, configuration{Routes: []routeConfig{route, route}}} {
		write(config)
		if _, err := Load(path, clock); !errors.Is(err, ErrConfig) {
			t.Fatal("invalid config accepted", err)
		}
	}
	wrong := route
	wrong.KeyFile = "missing.p8"
	write(configuration{Routes: []routeConfig{wrong}})
	if _, err := Load(path, clock); !errors.Is(err, ErrConfig) {
		t.Fatal("missing key accepted")
	}
	canceled, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := gateway.SendPreview(canceled, testAddress(t, "apns"), testID(t)); !errors.Is(err, context.Canceled) {
		t.Fatal("cancellation", err)
	}
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

func TestNetworkUncertaintyAndInFlightCancellationNeverExposeTokens(t *testing.T) {
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	address := testAddress(t, "apns")
	clock := &testClock{address.RegisteredAt.Add(time.Minute)}
	var calls atomic.Int64
	client := &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
		calls.Add(1)
		return nil, fmt.Errorf("private transport URL /3/device/%s", address.Spec.Token)
	})}
	sender, err := newAPNs(address.Spec.Route, "TEAMID0001", "KEYID00001", key, clock, client)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := sender.SendPreview(context.Background(), address, testID(t)); !errors.Is(err, ErrUnavailable) || strings.Contains(fmt.Sprintf("%v", err), address.Spec.Token) || calls.Load() != 1 {
		t.Fatal("transport privacy or retry")
	}
	started := make(chan struct{})
	server := tlsServer(func(w http.ResponseWriter, r *http.Request) { close(started); <-r.Context().Done() })
	defer server.Close()
	sender.endpoint = server.URL
	sender.client = clientFor(server.Client().Transport.(*http.Transport))
	ctx, cancel := context.WithCancel(context.Background())
	result := make(chan error, 1)
	preview := testID(t)
	go func() { _, err := sender.SendPreview(ctx, address, preview); result <- err }()
	select {
	case <-started:
		cancel()
	case <-time.After(3 * time.Second):
		cancel()
		t.Fatal("request did not reach provider")
	}
	select {
	case err := <-result:
		if !errors.Is(err, context.Canceled) {
			t.Fatal("provider cancellation", err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("canceled wait did not end")
	}
}
