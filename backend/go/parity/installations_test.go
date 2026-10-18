package parity

import (
	"context"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	receiver "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/events"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/records"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/security"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"os"
	"testing"
)

type admittedRetirement struct {
	called bool
	value  app.SessionRevocation
}

func (c *admittedRetirement) Handle(_ context.Context, r app.SessionRevocation) error {
	if e := r.Validate(); e != nil {
		return e
	}
	c.called = true
	c.value = r
	return nil
}
func TestSharedInstallations(t *testing.T) {
	read := func(name string, into any) {
		t.Helper()
		b, e := os.ReadFile("../../../contracts/fixtures/backend-installations/" + name + ".json")
		if e != nil {
			t.Fatal(e)
		}
		if e = json.Unmarshal(b, into); e != nil {
			t.Fatal(e)
		}
	}
	var proofs []struct {
		Name, Raw, SHA256 string
		Valid             bool
	}
	read("proofs", &proofs)
	for _, c := range proofs {
		got, e := (security.InstallationProofs{}).Digest(context.Background(), c.Raw)
		if (e == nil) != c.Valid {
			t.Fatal(c.Name, " proof admission differs")
		}
		if e == nil && (got != c.SHA256 || !records.ProofMatches(got, got)) {
			t.Fatal(c.Name, " proof digest differs")
		}
	}
	var specs []struct {
		Name   string
		Valid  bool
		Input  struct{ Provider, Application, Environment, Token string }
		Target string `json:"target_sha256"`
	}
	read("specs", &specs)
	for _, c := range specs {
		s := domain.InstallationSpec{Route: domain.PushRoute{Provider: c.Input.Provider, Application: c.Input.Application, Environment: c.Input.Environment}, Token: c.Input.Token}
		if (s.Validate() == nil) != c.Valid {
			t.Fatal(c.Name, " spec admission differs")
		}
		if c.Valid && records.InstallationTarget(s) != c.Target {
			t.Fatal(c.Name, " target digest differs")
		}
	}
	var cases []struct {
		Name     string
		Valid    bool
		Envelope json.RawMessage
		DataWire *string `json:"data_wire"`
		SHA256   string
	}
	read("events", &cases)
	for _, c := range cases {
		var m events.Envelope
		capture := &admittedRetirement{}
		e := json.Unmarshal(c.Envelope, &m)
		if e == nil {
			if c.DataWire != nil {
				m.Data = []byte(*c.DataWire)
			}
			e = receiver.SubscribeIdentity(capture).Handle(context.Background(), m)
		}
		if (e == nil && capture.called) != c.Valid {
			t.Fatal(c.Name, " retirement admission differs")
		}
		if e == nil && records.RevocationFingerprint(capture.value) != c.SHA256 {
			t.Fatal(c.Name, " retirement digest differs")
		}
	}
}
