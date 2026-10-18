package push

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"sync/atomic"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
)

// Repository-only corpus; no production endpoint override or runtime selection.
func TestSharedProviderResponses(t *testing.T) {
	var fixture struct {
		RegisteredAt int64  `json:"registered_at_us"`
		Now          int64  `json:"now_us"`
		Project      string `json:"project"`
		Cases        []struct {
			Name, Provider, Body, Expected string
			Status                         int
		}
	}
	wire, err := os.ReadFile("../../../../../../contracts/fixtures/backend-push-providers/responses.json")
	if err != nil {
		t.Fatal(err)
	}
	if err = json.Unmarshal(wire, &fixture); err != nil {
		t.Fatal(err)
	}
	for _, provider := range []string{"apns", "fcm"} {
		t.Run(provider, func(t *testing.T) {
			address := testAddress(t, provider)
			address.RegisteredAt = time.UnixMicro(fixture.RegisteredAt)
			clock := &testClock{time.UnixMicro(fixture.Now)}
			var response atomic.Pointer[struct {
				Status int
				Body   string
			}]
			var calls atomic.Int64
			server := tlsServer(func(w http.ResponseWriter, r *http.Request) {
				if r.URL.Path == "/token" {
					_, _ = io.WriteString(w, `{"access_token":"test-only-access","token_type":"Bearer","expires_in":3600}`)
					return
				}
				calls.Add(1)
				row := response.Load()
				w.WriteHeader(row.Status)
				_, _ = io.WriteString(w, row.Body)
			})
			defer server.Close()
			var sender app.PushGateway
			if provider == "apns" {
				key, e := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
				if e != nil {
					t.Fatal(e)
				}
				value, e := newAPNs(address.Spec.Route, "TEAMID0001", "KEYID00001", key, clock, clientFor(server.Client().Transport.(*http.Transport)))
				if e != nil {
					t.Fatal(e)
				}
				value.endpoint = server.URL
				sender = value
			} else {
				key, e := rsa.GenerateKey(rand.Reader, 2048)
				if e != nil {
					t.Fatal(e)
				}
				credentials, _ := json.Marshal(serviceAccount{Type: "service_account", ProjectID: fixture.Project, PrivateKeyID: "test-key", PrivateKey: string(testPEM(t, key)), ClientEmail: "sender@test-project.iam.gserviceaccount.com", TokenURI: oauthEndpoint})
				value, e := newFCM(address.Spec.Route, fixture.Project, credentials, clock, clientFor(server.Client().Transport.(*http.Transport)))
				if e != nil {
					t.Fatal(e)
				}
				value.endpoint = server.URL + "/send"
				value.tokenURL = server.URL + "/token"
				sender = value
			}
			for _, row := range fixture.Cases {
				if row.Provider != provider {
					continue
				}
				t.Run(row.Name, func(t *testing.T) {
					response.Store(&struct {
						Status int
						Body   string
					}{row.Status, row.Body})
					before := calls.Load()
					result, e := sender.SendPreview(context.Background(), address, testID(t))
					actual := string(result)
					if e != nil {
						actual = apperrors.Public(e).Code
					}
					if actual != row.Expected || calls.Load() != before+1 {
						t.Fatalf("outcome %q, expected %q; one send required", actual, row.Expected)
					}
				})
			}
		})
	}
}
