package account

import (
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"os"
	"testing"
	"time"
)

func TestPortableNameAdmissionAndCanonicalRestoration(t *testing.T) {
	wire, err := os.ReadFile("../../../../contracts/fixtures/account/names.json")
	if err != nil {
		t.Fatal(err)
	}
	var fixtures []struct {
		Input     string
		Valid     bool
		Canonical string
	}
	if err := json.Unmarshal(wire, &fixtures); err != nil {
		t.Fatal(err)
	}
	for _, fixture := range fixtures {
		name, err := domain.Name(fixture.Input)
		if (err == nil) != fixture.Valid || (fixture.Valid && name != fixture.Canonical) {
			t.Fatalf("name admission: %q %q %v", fixture.Input, name, err)
		}
	}
	identity := id.ID("00000000-0000-4000-8000-000000000001")
	now := time.Date(2026, 10, 13, 0, 0, 0, 123456789, time.FixedZone("offset", 3600))
	account, err := domain.New(identity, identity, "  Personal  ", now)
	if err != nil {
		t.Fatal(err)
	}
	snapshot := account.Snapshot()
	if snapshot.Name != "Personal" || snapshot.CreatedAt.Location() != time.UTC || snapshot.CreatedAt.Nanosecond()%1000 != 0 {
		t.Fatal("creation not canonical")
	}
	snapshot.Name = " Personal "
	if _, err := domain.Restore(snapshot); err == nil {
		t.Fatal("restore normalized corrupt name")
	}
	copy := account.Snapshot()
	copy.Name = "Mutated"
	if account.Snapshot().Name != "Personal" {
		t.Fatal("snapshot mutated aggregate")
	}
	if _, err := account.Rename("Next", account.Snapshot().CreatedAt.Add(-time.Second)); err == nil {
		t.Fatal("backwards timestamp admitted")
	}
}
