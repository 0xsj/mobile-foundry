package parity

import (
	"context"
	"encoding/hex"
	"encoding/json"
	audit "github.com/0xsj/mobile-foundry/backend/internal/audit/app/command"
	receiver "github.com/0xsj/mobile-foundry/backend/internal/audit/infra/events"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/records"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"os"
	"testing"
)

func TestSharedFilesPolicyFingerprintsEvents(t *testing.T) {
	read := func(name string, into any) {
		t.Helper()
		b, e := os.ReadFile("../../../contracts/fixtures/backend-files/" + name + ".json")
		if e != nil {
			t.Fatal(e)
		}
		if e = json.Unmarshal(b, into); e != nil {
			t.Fatal(e)
		}
	}
	var specs []struct {
		Name  string
		Input struct {
			Name        string
			ContentType string `json:"content_type"`
			ByteSize    int64  `json:"byte_size"`
			SHA256      string
		}
		Valid     bool
		Canonical struct{ Name string }
	}
	read("specs", &specs)
	for _, c := range specs {
		s, e := domain.Normalize(domain.Spec{Name: c.Input.Name, ContentType: c.Input.ContentType, ByteSize: c.Input.ByteSize, SHA256: c.Input.SHA256})
		if (e == nil) != c.Valid || c.Valid && s.Name != c.Canonical.Name {
			t.Fatal(c.Name, " specification differs")
		}
	}
	var prints []struct {
		Input struct {
			Action      string
			FileID      string `json:"file_id"`
			Name        string
			ContentType string `json:"content_type"`
			ByteSize    int64  `json:"byte_size"`
			SHA256      string
			Expected    uint64 `json:"expected_version"`
		}
		SHA256 string
	}
	read("fingerprints", &prints)
	for _, c := range prints {
		spec := domain.Spec{}
		if c.Input.Action == app.Request {
			spec = domain.Spec{Name: c.Input.Name, ContentType: c.Input.ContentType, ByteSize: c.Input.ByteSize, SHA256: c.Input.SHA256}
		}
		sum := records.Fingerprint(app.Command{Action: c.Input.Action, FileID: id.ID(c.Input.FileID), Spec: spec, Expected: c.Input.Expected})
		if hex.EncodeToString(sum[:]) != c.SHA256 {
			t.Fatal("Files v1 fingerprint differs")
		}
	}
	var cases []struct {
		Name     string
		Valid    bool
		Envelope json.RawMessage
	}
	read("events", &cases)
	for _, c := range cases {
		var m events.Envelope
		e := json.Unmarshal(c.Envelope, &m)
		if e == nil {
			e = receiver.SubscribeFiles(audit.Record{Store: memory.New(), Clock: clock.System{}}).Handle(context.Background(), m)
		}
		if (e == nil) != c.Valid {
			t.Fatal(c.Name, " Files event differs")
		}
	}
}
