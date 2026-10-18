package parity

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"testing"

	"github.com/0xsj/mobile-foundry/backend/internal/notes/domain"
)

func TestSharedNotesPolicyAndFingerprint(t *testing.T) {
	wire, err := os.ReadFile("../../../contracts/fixtures/notes/create-titles.json")
	if err != nil {
		t.Fatal(err)
	}
	var titles []struct {
		Title string
		Error *string
	}
	if err = json.Unmarshal(wire, &titles); err != nil {
		t.Fatal(err)
	}
	for _, test := range titles {
		if domain.ValidTitle(test.Title) != (test.Error == nil) {
			t.Fatal("title policy differs")
		}
	}
	wire, err = os.ReadFile("../../../contracts/fixtures/backend-notes/fingerprints.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []struct {
		Input struct {
			CommandID string `json:"command_id"`
			Action    string `json:"action"`
			NoteID    string `json:"note_id"`
			Title     string `json:"title"`
			Expected  uint64 `json:"expected_version"`
		}
		SHA256 string `json:"sha256"`
	}
	if err = json.Unmarshal(wire, &cases); err != nil {
		t.Fatal(err)
	}
	for _, test := range cases {
		payload, err := json.Marshal(test.Input)
		if err != nil {
			t.Fatal(err)
		}
		sum := sha256.Sum256(append([]byte("notes.write.v1\n"), payload...))
		if hex.EncodeToString(sum[:]) != test.SHA256 {
			t.Fatal("Notes v1 fingerprint differs")
		}
	}
}
