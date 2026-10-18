package parity

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
)

func TestSharedAccountNames(t *testing.T) {
	wire, err := os.ReadFile("../../../contracts/fixtures/account/names.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []struct {
		Input     string  `json:"input"`
		Valid     bool    `json:"valid"`
		Canonical *string `json:"canonical"`
	}
	if err := json.Unmarshal(wire, &cases); err != nil {
		t.Fatal(err)
	}
	for _, test := range cases {
		name, err := domain.Name(test.Input)
		if (err == nil) != test.Valid {
			t.Fatal("account name validity differs")
		}
		if test.Canonical != nil && name != *test.Canonical {
			t.Fatal("canonical account name differs")
		}
	}
}
