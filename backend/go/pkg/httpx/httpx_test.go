package httpx

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

func TestJSONAdmission(t *testing.T) {
	for _, test := range []struct {
		body, contentType string
		valid             bool
	}{{`{"name":"Ada"}`, "application/json", true}, {`{"name":"Ada"}`, "application/json; charset=utf-8", true}, {`{"name":"Ada","extra":1}`, "application/json", false}, {`null`, "application/json", false}, {`[]`, "application/json", false}, {`{} {}`, "application/json", false}, {`{"name":`, "application/json", false}, {`{}`, "text/plain", false}, {strings.Repeat(" ", 16385), "application/json", false}} {
		r := httptest.NewRequest("POST", "/", strings.NewReader(test.body))
		r.Header.Set("Content-Type", test.contentType)
		var body struct {
			Name string `json:"name"`
		}
		if err := DecodeJSON(httptest.NewRecorder(), r, &body); (err == nil) != test.valid {
			t.Errorf("admission %q: %v", test.body, err)
		}
	}
}

func TestProjectionMetadataMethodsAndDefects(t *testing.T) {
	var logs strings.Builder
	log := slog.New(slog.NewJSONHandler(&logs, nil))
	private := errors.New("secret-token-123")
	endpoint := Methods(map[string]Endpoint{"GET": func(w http.ResponseWriter, r *http.Request) error {
		return apperrors.New(apperrors.Internal, "private.code", "private text").WithCause(private)
	}, "POST": func(http.ResponseWriter, *http.Request) error { panic(private) }})
	for _, method := range []string{"GET", "HEAD", "POST", "DELETE"} {
		r := httptest.NewRequest(method, "/", nil)
		r.Header.Set("X-Correlation-ID", "work-123")
		r.Header.Set("X-Request-ID", "caller-cannot-pick")
		w := httptest.NewRecorder()
		Boundary(endpoint, id.Random{}, log).ServeHTTP(w, r)
		if w.Header().Get("X-Request-ID") == "caller-cannot-pick" || w.Header().Get("X-Correlation-ID") != "work-123" {
			t.Fatal("metadata ownership lost")
		}
		if _, err := id.Parse(w.Header().Get("X-Request-ID")); err != nil {
			t.Fatal("missing request ID")
		}
		if method == "HEAD" {
			if w.Body.Len() != 0 {
				t.Fatal("HEAD body emitted")
			}
			continue
		}
		var problem Problem
		if err := json.Unmarshal(w.Body.Bytes(), &problem); err != nil {
			t.Fatal(err)
		}
		expected := 500
		if method == "DELETE" {
			expected = 405
		}
		if w.Code != expected || problem.RequestID != w.Header().Get("X-Request-ID") {
			t.Fatalf("bad problem %d %+v", w.Code, problem)
		}
		if expected == 500 && (problem.Code != "" || problem.Detail != "An unexpected error occurred.") {
			t.Fatal("diagnostics exposed")
		}
	}
	if strings.Contains(logs.String(), "secret-token") || strings.Contains(logs.String(), "private text") {
		t.Fatal("default logs exposed private payload")
	}
	r := httptest.NewRequest("GET", "/", nil)
	r.Header.Add("X-Correlation-ID", "one")
	r.Header.Add("X-Correlation-ID", "two")
	w := httptest.NewRecorder()
	Boundary(endpoint, id.Random{}, log).ServeHTTP(w, r)
	if w.Code != 400 {
		t.Fatal("repeated correlation ID accepted")
	}
}

func TestVersionKeyAndCredentialAdmission(t *testing.T) {
	for _, value := range []string{`W/"1"`, `*`, `"01"`, `"0"`, `"9007199254740992"`, `"1","2"`} {
		r := httptest.NewRequest("PATCH", "/", nil)
		r.Header.Set("If-Match", value)
		if _, err := RequiredVersion(r); err == nil {
			t.Fatalf("admitted %s", value)
		}
	}
	r := httptest.NewRequest("PATCH", "/", nil)
	if _, err := RequiredVersion(r); !errors.Is(err, ErrPreconditionRequired) {
		t.Fatal("missing precondition not distinguished")
	}
	r.Header.Set("If-Match", `"9007199254740991"`)
	if version, err := RequiredVersion(r); err != nil || version != 9007199254740991 {
		t.Fatal("exact maximum version rejected")
	}
	r.Header.Set("Idempotency-Key", "command-123")
	if key, err := IdempotencyKey(r); err != nil || key != "command-123" {
		t.Fatal("valid key rejected")
	}
	r.Header.Add("Idempotency-Key", "command-456")
	if _, err := IdempotencyKey(r); err == nil {
		t.Fatal("repeated key accepted")
	}
	r.Header.Set("Authorization", "Bearer some-token")
	if _, err := RequirePrincipal(r, principal.Deny{}); !errors.Is(err, principal.ErrRequired) {
		t.Fatal("deny resolver granted access")
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	r = r.WithContext(ctx)
	if _, err := RequirePrincipal(r, principal.Deny{}); !errors.Is(err, context.Canceled) {
		t.Fatal("credential resolution ignored cancellation")
	}
}

func TestResponseEncodingBeforeCommit(t *testing.T) {
	w := httptest.NewRecorder()
	r := httptest.NewRequest("GET", "/", nil)
	if err := WriteJSON(w, r, 200, make(chan int)); err == nil || w.Body.Len() != 0 || len(w.Header()) != 0 {
		t.Fatal("encoding failure committed a response")
	}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	endpoint := Methods(map[string]Endpoint{"GET": func(w http.ResponseWriter, r *http.Request) error { return WriteJSON(w, r, 200, make(chan int)) }})
	Boundary(endpoint, id.Random{}, log).ServeHTTP(w, r)
	if w.Code != 500 {
		t.Fatalf("failed encoding produced %d", w.Code)
	}
}
