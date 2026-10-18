package parity

import (
	"context"
	"encoding/json"
	"os"
	"testing"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/security"
)

func TestSharedIdentityPolicyCorpus(t *testing.T) {
	wire, err := os.ReadFile("../../../contracts/fixtures/backend-identity/policy.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []struct {
		Field    string  `json:"field"`
		Input    string  `json:"input"`
		Valid    bool    `json:"valid"`
		Expected *string `json:"expected"`
	}
	if err := json.Unmarshal(wire, &cases); err != nil {
		t.Fatal(err)
	}
	for _, test := range cases {
		var value string
		switch test.Field {
		case "login":
			value, err = domain.Login(test.Input)
		case "display_name":
			value, err = domain.DisplayName(test.Input)
		default:
			err = domain.Password(test.Input)
		}
		if (err == nil) != test.Valid {
			t.Fatalf("policy differs: %s", test.Field)
		}
		if test.Expected != nil && value != *test.Expected {
			t.Fatalf("canonical value differs: %s", test.Field)
		}
	}
}

func TestSharedIdentitySecurityVectors(t *testing.T) {
	wire, err := os.ReadFile("../../../contracts/fixtures/backend-identity/security.json")
	if err != nil {
		t.Fatal(err)
	}
	var v map[string]string
	if err := json.Unmarshal(wire, &v); err != nil {
		t.Fatal(err)
	}
	passwords, err := security.NewPasswords()
	if err != nil {
		t.Fatal(err)
	}
	match, err := passwords.Verify(context.Background(), v["hash"], v["password"])
	if err != nil || !match {
		t.Fatal("password vector differs")
	}
	for _, purpose := range []string{"access", "refresh"} {
		digest, err := (security.Tokens{}).Digest(context.Background(), v[purpose+"_token"], purpose)
		if err != nil || digest != v[purpose+"_digest"] {
			t.Fatal("token digest differs")
		}
	}
}
