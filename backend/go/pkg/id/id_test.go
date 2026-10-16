package id

import (
	"errors"
	"io"
	"strings"
	"testing"
)

func TestCanonicalAndGeneration(t *testing.T) {
	for _, invalid := range []string{"", "00000000-0000-0000-0000-000000000000", "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA", "12345678-1234-1234-1234-12345678901z", "12345678123412341234123456789012"} {
		if _, err := Parse(invalid); err == nil {
			t.Fatalf("admitted %q", invalid)
		}
	}
	value, err := generate(strings.NewReader(strings.Repeat("\x00", 16)))
	if err != nil || value != "00000000-0000-4000-8000-000000000000" || !value.Valid() {
		t.Fatalf("bad generated UUID: %s %v", value, err)
	}
	if _, err := generate(strings.NewReader("short")); !errors.Is(err, io.ErrUnexpectedEOF) {
		t.Fatal("entropy failure disappeared")
	}
	random, err := (Random{}).NewID()
	if err != nil || !random.Valid() {
		t.Fatalf("crypto generation failed: %v", err)
	}
}
