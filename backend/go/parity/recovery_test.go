package parity

import (
	"context"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app/command"
	receiver "github.com/0xsj/mobile-foundry/backend/internal/audit/infra/events"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"os"
	"testing"
)

func TestSharedRecoveryPolicyAndEvents(t *testing.T) {
	wire, err := os.ReadFile("../../../contracts/fixtures/backend-recovery/emails.json")
	if err != nil {
		t.Fatal(err)
	}
	var emails []struct {
		Input     string
		Valid     bool
		Canonical *string
	}
	if err = json.Unmarshal(wire, &emails); err != nil {
		t.Fatal(err)
	}
	for _, c := range emails {
		v, err := domain.Email(c.Input)
		if (err == nil) != c.Valid || (c.Canonical != nil && v != *c.Canonical) {
			t.Fatal("email policy differs")
		}
	}
	wire, err = os.ReadFile("../../../contracts/fixtures/backend-recovery/events.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []struct {
		Valid    bool
		Envelope json.RawMessage
	}
	if err = json.Unmarshal(wire, &cases); err != nil {
		t.Fatal(err)
	}
	for _, c := range cases {
		var message events.Envelope
		err = json.Unmarshal(c.Envelope, &message)
		if err == nil {
			err = receiver.SubscribeIdentity(command.Record{Store: memory.New(), Clock: clock.System{}}).Handle(context.Background(), message)
		}
		if (err == nil) != c.Valid {
			t.Fatal("recovery event admission differs")
		}
	}
}
