package push

import (
	"bytes"
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/mail"
	"net/url"
	"regexp"
	"strings"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const oauthEndpoint = "https://oauth2.googleapis.com/token"

type serviceAccount struct {
	Type          string `json:"type"`
	ProjectID     string `json:"project_id"`
	PrivateKeyID  string `json:"private_key_id"`
	PrivateKey    string `json:"private_key"`
	ClientEmail   string `json:"client_email"`
	ClientID      string `json:"client_id"`
	AuthURI       string `json:"auth_uri"`
	TokenURI      string `json:"token_uri"`
	AuthCertURL   string `json:"auth_provider_x509_cert_url"`
	ClientCertURL string `json:"client_x509_cert_url"`
	Universe      string `json:"universe_domain"`
}

func (serviceAccount) String() string   { return "push.serviceAccount{redacted}" }
func (serviceAccount) GoString() string { return "push.serviceAccount{redacted}" }

type fcmSender struct {
	route                 domain.PushRoute
	project, email, keyID string
	key                   *rsa.PrivateKey
	clock                 clock.Clock
	client                *http.Client
	tokenURL, endpoint    string
	gate                  chan struct{}
	cached                string
	issued, expires       time.Time
}

func (*fcmSender) String() string   { return "push.fcmSender{redacted}" }
func (*fcmSender) GoString() string { return "push.fcmSender{redacted}" }
func newFCM(route domain.PushRoute, project string, wire []byte, clock clock.Clock, client *http.Client) (*fcmSender, error) {
	var c serviceAccount
	if decode(wire, &c) != nil || c.Type != "service_account" || c.ProjectID != project || c.TokenURI != oauthEndpoint || c.Universe != "" && c.Universe != "googleapis.com" || !regexp.MustCompile(`^[a-z][a-z0-9-]{4,28}[a-z0-9]$`).MatchString(project) {
		return nil, ErrConfig
	}
	address, err := mail.ParseAddress(c.ClientEmail)
	if err != nil || address.Address != c.ClientEmail || c.PrivateKeyID == "" {
		return nil, ErrConfig
	}
	key, err := parseKey([]byte(c.PrivateKey))
	if err != nil {
		return nil, ErrConfig
	}
	private, ok := key.(*rsa.PrivateKey)
	if !ok || private.N.BitLen() < 2048 || private.Validate() != nil {
		return nil, ErrConfig
	}
	return &fcmSender{route: route, project: project, email: c.ClientEmail, keyID: c.PrivateKeyID, key: private, clock: clock, client: client, tokenURL: oauthEndpoint, endpoint: "https://fcm.googleapis.com/v1/projects/" + project + "/messages:send", gate: make(chan struct{}, 1)}, nil
}
func (s *fcmSender) authorization(ctx context.Context) (string, error) {
	select {
	case s.gate <- struct{}{}:
		defer func() { <-s.gate }()
	case <-ctx.Done():
		return "", ctx.Err()
	}
	now := s.clock.Now()
	if s.cached != "" && !now.Before(s.issued) && now.Before(s.expires.Add(-time.Minute)) {
		return s.cached, nil
	}
	header, _ := json.Marshal(map[string]string{"alg": "RS256", "typ": "JWT", "kid": s.keyID})
	claims, _ := json.Marshal(map[string]any{"iss": s.email, "scope": "https://www.googleapis.com/auth/firebase.messaging", "aud": oauthEndpoint, "iat": now.Unix(), "exp": now.Add(time.Hour).Unix()})
	encode := base64.RawURLEncoding.EncodeToString
	input := encode(header) + "." + encode(claims)
	digest := sha256.Sum256([]byte(input))
	signature, err := rsa.SignPKCS1v15(rand.Reader, s.key, crypto.SHA256, digest[:])
	if err != nil {
		return "", ErrCredentials
	}
	form := url.Values{"grant_type": {"urn:ietf:params:oauth:grant-type:jwt-bearer"}, "assertion": {input + "." + encode(signature)}}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, s.tokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return "", ErrConfig
	}
	request.Header.Set("content-type", "application/x-www-form-urlencoded")
	status, wire, err := response(s.client, request)
	if err != nil {
		return "", err
	}
	if status != 200 {
		return "", providerError(status)
	}
	var token struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int64  `json:"expires_in"`
		TokenType   string `json:"token_type"`
	}
	if json.Unmarshal(wire, &token) != nil || !strings.EqualFold(token.TokenType, "Bearer") || token.ExpiresIn < 1 || token.ExpiresIn > 3600 || len(token.AccessToken) < 1 || len(token.AccessToken) > 4096 {
		return "", ErrUnavailable
	}
	for _, v := range []byte(token.AccessToken) {
		if v < 33 || v > 126 {
			return "", ErrUnavailable
		}
	}
	s.cached = token.AccessToken
	s.issued = now
	s.expires = now.Add(time.Duration(token.ExpiresIn) * time.Second)
	return s.cached, nil
}
func (s *fcmSender) SendPreview(ctx context.Context, address app.PushAddress, preview id.ID) (app.PushDisposition, error) {
	if address.Spec.Route != s.route || address.Spec.Validate() != nil {
		return "", ErrRoute
	}
	auth, err := s.authorization(ctx)
	if err != nil {
		return "", err
	}
	payload := map[string]any{"message": map[string]any{"token": address.Spec.Token, "notification": map[string]string{"title": previewTitle, "body": previewBody}, "data": previewData(preview), "android": map[string]any{"ttl": "0s", "restricted_package_name": s.route.Application}}}
	wire, _ := json.Marshal(payload)
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, s.endpoint, bytes.NewReader(wire))
	if err != nil {
		return "", ErrConfig
	}
	request.Header.Set("content-type", "application/json")
	request.Header.Set("authorization", "Bearer "+auth)
	status, body, err := response(s.client, request)
	if err != nil {
		return "", err
	}
	if status == 200 {
		var accepted struct {
			Name string `json:"name"`
		}
		if json.Unmarshal(body, &accepted) == nil && strings.HasPrefix(accepted.Name, "projects/"+s.project+"/messages/") && len(accepted.Name) > len("projects/"+s.project+"/messages/") {
			return app.PushAccepted, nil
		}
		return "", ErrUnavailable
	}
	var failure struct {
		Error struct {
			Code    int    `json:"code"`
			Status  string `json:"status"`
			Details []struct {
				Type      string `json:"@type"`
				ErrorCode string `json:"errorCode"`
			} `json:"details"`
		} `json:"error"`
	}
	if status == 404 && json.Unmarshal(body, &failure) == nil && failure.Error.Code == 404 && failure.Error.Status == "NOT_FOUND" {
		for _, d := range failure.Error.Details {
			if d.Type == "type.googleapis.com/google.firebase.fcm.v1.FcmError" && d.ErrorCode == "UNREGISTERED" {
				return app.PushInvalidTarget, nil
			}
		}
	}
	return "", providerError(status)
}
