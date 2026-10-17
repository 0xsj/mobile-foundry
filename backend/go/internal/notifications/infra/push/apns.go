package push

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type apnsSender struct {
	route       domain.PushRoute
	team, keyID string
	key         *ecdsa.PrivateKey
	clock       clock.Clock
	client      *http.Client
	endpoint    string
	mu          sync.Mutex
	cached      string
	issued      time.Time
}

func (*apnsSender) String() string   { return "push.apnsSender{redacted}" }
func (*apnsSender) GoString() string { return "push.apnsSender{redacted}" }
func newAPNs(route domain.PushRoute, team, keyID string, key any, clock clock.Clock, client *http.Client) (*apnsSender, error) {
	private, ok := key.(*ecdsa.PrivateKey)
	valid := regexp.MustCompile(`^[A-Z0-9]{10}$`)
	if !ok || private.Curve != elliptic.P256() || !valid.MatchString(team) || !valid.MatchString(keyID) {
		return nil, ErrConfig
	}
	endpoint := "https://api.push.apple.com"
	if route.Environment == "sandbox" {
		endpoint = "https://api.sandbox.push.apple.com"
	}
	return &apnsSender{route: route, team: team, keyID: keyID, key: private, clock: clock, client: client, endpoint: endpoint}, nil
}
func (s *apnsSender) authorization() (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := s.clock.Now()
	if s.cached != "" && !now.Before(s.issued) && now.Sub(s.issued) < 50*time.Minute {
		return s.cached, nil
	}
	header, _ := json.Marshal(map[string]string{"alg": "ES256", "kid": s.keyID})
	claims, _ := json.Marshal(map[string]any{"iss": s.team, "iat": now.Unix()})
	encode := base64.RawURLEncoding.EncodeToString
	input := encode(header) + "." + encode(claims)
	digest := sha256.Sum256([]byte(input))
	r, v, err := ecdsa.Sign(rand.Reader, s.key, digest[:])
	if err != nil {
		return "", ErrCredentials
	}
	signature := append(r.FillBytes(make([]byte, 32)), v.FillBytes(make([]byte, 32))...)
	s.cached = input + "." + encode(signature)
	s.issued = now
	return s.cached, nil
}
func (s *apnsSender) SendPreview(ctx context.Context, address app.PushAddress, preview id.ID) (app.PushDisposition, error) {
	token := address.Spec.Token
	if address.Spec.Route != s.route || token != strings.ToLower(token) || len(token) < 2 || len(token) > 4096 {
		return "", ErrRoute
	}
	if _, err := hex.DecodeString(token); err != nil {
		return "", ErrRoute
	}
	auth, err := s.authorization()
	if err != nil {
		return "", err
	}
	payload := map[string]any{"aps": map[string]any{"alert": map[string]string{"title": previewTitle, "body": previewBody}, "sound": "default"}}
	for k, v := range previewData(preview) {
		payload[k] = v
	}
	wire, _ := json.Marshal(payload)
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, s.endpoint+"/3/device/"+token, bytes.NewReader(wire))
	if err != nil {
		return "", ErrRoute
	}
	request.Header.Set("authorization", "bearer "+auth)
	request.Header.Set("content-type", "application/json")
	request.Header.Set("apns-topic", s.route.Application)
	request.Header.Set("apns-push-type", "alert")
	request.Header.Set("apns-priority", "10")
	request.Header.Set("apns-expiration", "0")
	request.Header.Set("apns-id", preview.String())
	status, body, err := response(s.client, request)
	if err != nil {
		return "", err
	}
	if status == 200 && len(body) == 0 {
		return app.PushAccepted, nil
	}
	var failure struct {
		Reason    string `json:"reason"`
		Timestamp int64  `json:"timestamp"`
	}
	if json.Unmarshal(body, &failure) == nil {
		if status == 400 && failure.Reason == "BadDeviceToken" {
			return app.PushInvalidTarget, nil
		}
		if status == 410 && failure.Reason == "Unregistered" && failure.Timestamp > 0 && !time.UnixMilli(failure.Timestamp).Before(address.RegisteredAt) && !time.UnixMilli(failure.Timestamp).After(s.clock.Now().Add(5*time.Minute)) {
			return app.PushInvalidTarget, nil
		}
	}
	return "", providerError(status)
}
