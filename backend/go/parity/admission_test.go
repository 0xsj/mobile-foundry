package parity

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"strings"
	"testing"

	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type admissionCase struct {
	Name      string         `json:"name"`
	Operation string         `json:"operation"`
	Body      string         `json:"body"`
	PadTo     int            `json:"pad_to"`
	Headers   [][2]string    `json:"headers"`
	Expected  map[string]any `json:"expected"`
}

func readCases(t *testing.T, file string, target any) {
	t.Helper()
	wire, err := os.ReadFile("../../../contracts/fixtures/backend-runtime/" + file)
	if err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal(wire, target); err != nil {
		t.Fatal(err)
	}
}
func checkAdmission(t *testing.T, test admissionCase, value any, err error) {
	t.Helper()
	actual := map[string]any{"status": 200, "value": value}
	if err != nil {
		w := httptest.NewRecorder()
		if e := httpx.WriteError(w, httptest.NewRequest("POST", "/", nil), err); e != nil {
			t.Fatal(e)
		}
		var problem map[string]any
		if e := json.Unmarshal(w.Body.Bytes(), &problem); e != nil {
			t.Fatal(e)
		}
		if problem["kind"] != "invalid" {
			t.Fatalf("unexpected kind: %v", problem["kind"])
		}
		actual = map[string]any{"status": w.Code, "code": problem["code"], "detail": problem["detail"]}
	}
	wire, err := json.Marshal(actual)
	if err != nil {
		t.Fatal(err)
	}
	var normalized map[string]any
	if err := json.Unmarshal(wire, &normalized); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(normalized, test.Expected) {
		t.Fatalf("got %s, expected %v", wire, test.Expected)
	}
}

func TestSharedRuntimeJSONAdmissionCorpus(t *testing.T) {
	var cases []admissionCase
	readCases(t, "admission.json", &cases)
	for _, test := range cases {
		t.Run(test.Name, func(t *testing.T) {
			body := test.Body
			if test.PadTo > 0 {
				body += strings.Repeat(" ", test.PadTo-len(body))
			}
			r := httptest.NewRequest("POST", "/", strings.NewReader(body))
			for _, header := range test.Headers {
				r.Header.Add(header[0], header[1])
			}
			var value struct {
				Name   *string `json:"name"`
				Detail *struct {
					Enabled *bool `json:"enabled"`
				} `json:"detail"`
			}
			err := httpx.DecodeJSON(httptest.NewRecorder(), r, &value)
			checkAdmission(t, test, value, err)
		})
	}
}

func TestSharedRuntimeHeadersCorpus(t *testing.T) {
	var cases []admissionCase
	readCases(t, "headers.json", &cases)
	for _, test := range cases {
		t.Run(test.Name, func(t *testing.T) {
			r := httptest.NewRequest("PATCH", "/", nil)
			for _, header := range test.Headers {
				r.Header.Add(header[0], header[1])
			}
			var value any
			var err error
			switch test.Operation {
			case "version":
				var version uint64
				version, err = httpx.RequiredVersion(r)
				value = version
				if err == nil {
					w := httptest.NewRecorder()
					httpx.SetVersion(w, version)
					if w.Header().Get("ETag") != r.Header.Get("If-Match") {
						t.Fatal("ETag mismatch")
					}
				}
			case "key":
				var key string
				key, err = httpx.IdempotencyKey(r)
				if key != "" {
					value = key
				}
			default:
				t.Fatal("unknown operation")
			}
			checkAdmission(t, test, value, err)
		})
	}
}

func TestSharedRuntimeIdentifiersCorpus(t *testing.T) {
	var cases []struct {
		Value string `json:"value"`
		Valid bool   `json:"valid"`
	}
	readCases(t, "ids.json", &cases)
	for _, test := range cases {
		value, err := id.Parse(test.Value)
		if (err == nil) != test.Valid {
			t.Fatalf("identifier validity differed: %q", test.Value)
		}
		if err == nil && value.String() != test.Value {
			t.Fatal("identifier changed")
		}
	}
}

type refusedIDs struct{ invalid bool }

func (source refusedIDs) NewID() (id.ID, error) {
	if source.invalid {
		return id.ID("invalid"), nil
	}
	return "", errors.New("SECRET entropy failure")
}

func TestSharedRuntimeIDGenerationFailure(t *testing.T) {
	for _, invalid := range []bool{false, true} {
		for _, method := range []string{"GET", "HEAD"} {
			called := false
			var logs strings.Builder
			endpoint := http.HandlerFunc(func(http.ResponseWriter, *http.Request) { called = true })
			w := httptest.NewRecorder()
			handler := httpx.Boundary(endpoint, refusedIDs{invalid}, slog.New(slog.NewJSONHandler(&logs, nil)))
			handler.ServeHTTP(w, httptest.NewRequest(method, "/", nil))
			if called || w.Code != 500 || w.Header().Get("X-Request-ID") != "" {
				t.Fatal("ID failure admitted request")
			}
			if method == "HEAD" {
				if w.Body.Len() != 0 {
					t.Fatal("HEAD emitted body")
				}
			} else {
				var body map[string]any
				if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
					t.Fatal(err)
				}
				if body["code"] != nil || body["request_id"] != nil || body["detail"] != "An unexpected error occurred." {
					t.Fatal("unsafe ID failure")
				}
			}
			if strings.Contains(logs.String(), "SECRET") {
				t.Fatal("private generator diagnostic logged")
			}
		}
	}
}
