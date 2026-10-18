package parity

import (
	"context"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	receiver "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/events"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/records"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"os"
	"testing"
	"time"
)

type admittedNotice struct {
	called   bool
	delivery app.Delivery
}

func (c *admittedNotice) Handle(_ context.Context, d app.Delivery) error {
	c.called = true
	c.delivery = d
	return nil
}
func TestSharedNotificationsEventsAndFingerprints(t *testing.T) {
	read := func(name string, into any) {
		t.Helper()
		b, e := os.ReadFile("../../../contracts/fixtures/backend-notifications/" + name + ".json")
		if e != nil {
			t.Fatal(e)
		}
		if e = json.Unmarshal(b, into); e != nil {
			t.Fatal(e)
		}
	}
	var cases []struct {
		Name     string
		Valid    bool
		Envelope json.RawMessage
		DataWire *string `json:"data_wire"`
	}
	read("events", &cases)
	for _, c := range cases {
		var m events.Envelope
		capture := &admittedNotice{}
		err := json.Unmarshal(c.Envelope, &m)
		if err == nil {
			if c.DataWire != nil {
				m.Data = []byte(*c.DataWire)
			}
			err = receiver.SubscribeFiles(capture).Handle(context.Background(), m)
		}
		if (err == nil && capture.called) != c.Valid {
			t.Fatal(c.Name, " notification event admission differs")
		}
		if err == nil && (capture.delivery.EventID != m.ID || capture.delivery.PrincipalID != m.ActorID) {
			t.Fatal("recipient/source identity changed")
		}
	}
	var prints []struct {
		Input struct {
			Event     string `json:"event_id"`
			Account   string `json:"account_id"`
			Principal string `json:"principal_id"`
			File      string `json:"file_id"`
			At        string `json:"occurred_at"`
		}
		SHA256 string
	}
	read("fingerprints", &prints)
	for _, c := range prints {
		at, e := time.Parse(time.RFC3339Nano, c.Input.At)
		if e != nil {
			t.Fatal(e)
		}
		d := app.Delivery{EventID: id.ID(c.Input.Event), AccountID: id.ID(c.Input.Account), PrincipalID: id.ID(c.Input.Principal), FileID: id.ID(c.Input.File), OccurredAt: at}
		if e = d.Validate(); e != nil || records.Fingerprint(d) != c.SHA256 {
			t.Fatal("notification source fingerprint differs")
		}
	}
}
