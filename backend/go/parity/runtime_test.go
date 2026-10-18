package parity

import (
	"encoding/json"
	"errors"
	"net/http/httptest"
	"os"
	"testing"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
)

// Both implementations project this same transport-neutral corpus. These are
// test-only conditions; no diagnostic routes are exposed by either production API.
func TestSharedRuntimeFailureCorpus(t *testing.T) {
	wire, err := os.ReadFile("../../../contracts/fixtures/backend-runtime/failures.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []struct {
		Name     string            `json:"name"`
		Kind     apperrors.Kind    `json:"kind"`
		Code     string            `json:"code"`
		Public   *string           `json:"public"`
		Fields   map[string]string `json:"fields"`
		Expected map[string]any    `json:"expected"`
	}
	if err := json.Unmarshal(wire, &cases); err != nil {
		t.Fatal(err)
	}
	for _, test := range cases {
		t.Run(test.Name, func(t *testing.T) {
			condition := apperrors.New(test.Kind, test.Code, "SECRET diagnostic").WithFields(test.Fields).WithCause(errors.New("SECRET dependency"))
			if test.Public != nil {
				condition = condition.WithPublic(*test.Public)
			}
			response := httptest.NewRecorder()
			if err := httpx.WriteError(response, httptest.NewRequest("GET", "/", nil), condition); err != nil {
				t.Fatal(err)
			}
			var actual map[string]any
			if err := json.Unmarshal(response.Body.Bytes(), &actual); err != nil {
				t.Fatal(err)
			}
			// Direct projector has no boundary metadata. The corpus checks the same
			// public shape with a fixed test ID; live runner checks generated IDs.
			actual["request_id"] = "fixture-request"
			want, _ := json.Marshal(test.Expected)
			got, _ := json.Marshal(actual)
			if string(got) != string(want) || float64(response.Code) != test.Expected["status"] {
				t.Fatalf("projection: got %s (%d), want %s", got, response.Code, want)
			}
		})
	}
}
