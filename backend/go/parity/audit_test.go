package parity

import (
	"context"
	"encoding/json"
	"os"
	"testing"

	"github.com/0xsj/mobile-foundry/backend/internal/audit/app/command"
	client "github.com/0xsj/mobile-foundry/backend/internal/audit/infra/capabilities"
	receiver "github.com/0xsj/mobile-foundry/backend/internal/audit/infra/events"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func TestSharedAuditEventAdmission(t *testing.T) {
	wire, err := os.ReadFile("../../../contracts/fixtures/backend-audit/events.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []struct {
		Name, Source string
		Valid        bool
		Envelope     json.RawMessage
		DataWire     *string `json:"data_wire"`
	}
	if err = json.Unmarshal(wire, &cases); err != nil {
		t.Fatal(err)
	}
	for _, test := range cases {
		t.Run(test.Name, func(t *testing.T) {
			var message events.Envelope
			err := json.Unmarshal(test.Envelope, &message)
			if test.DataWire != nil {
				message.Data = []byte(*test.DataWire)
			}
			store := memory.New()
			recorder := command.Record{Store: store, Clock: clock.System{}}
			receive := receiver.Subscribe(recorder).Handle
			if test.Source == "identity" {
				receive = receiver.SubscribeIdentity(recorder).Handle
			}
			if err == nil {
				err = receive(context.Background(), message)
			}
			if (err == nil) != test.Valid {
				t.Fatal("event admission differs")
			}
			if test.Valid {
				page, _ := store.List(context.Background(), message.AccountID, 0, 100)
				if test.Source == "identity" {
					page, _ = store.ListPrincipal(context.Background(), message.ActorID, 0, 100)
				}
				if len(page.Records) != 1 {
					t.Fatal("admitted evidence missing")
				}
			}
		})
	}
}

type auditFixtureInvoker struct{ wire []byte }

func (i auditFixtureInvoker) Invoke(_ context.Context, name string, _ any, target any) error {
	if name != client.AccountAccess {
		return capability.ErrInvalid
	}
	return capability.Decode(i.wire, target)
}
func TestSharedAuditAccessResponses(t *testing.T) {
	wire, err := os.ReadFile("../../../contracts/fixtures/backend-audit/access-responses.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []struct {
		Name, Wire     string
		Valid, Allowed bool
	}
	if err = json.Unmarshal(wire, &cases); err != nil {
		t.Fatal(err)
	}
	identity := id.ID("00000000-0000-4000-8000-000000000001")
	for _, test := range cases {
		allowed, err := (client.Client{Invoker: auditFixtureInvoker{[]byte(test.Wire)}}).Allowed(context.Background(), identity, identity)
		if (err == nil) != test.Valid || (test.Valid && allowed != test.Allowed) {
			t.Fatalf("access response differs: %s", test.Name)
		}
	}
}
