// Package id owns opaque canonical UUID identifiers and their generation seam.
package id

import (
	"crypto/rand"
	"encoding/hex"
	"io"
	"strings"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
)

type ID string

var ErrInvalid = apperrors.New(apperrors.Invalid, "id.invalid", "invalid canonical UUID").WithPublic("Provide a valid identifier.")

func Parse(value string) (ID, error) {
	if len(value) != 36 || value != strings.ToLower(value) || value[8] != '-' || value[13] != '-' || value[18] != '-' || value[23] != '-' {
		return "", ErrInvalid
	}
	raw, err := hex.DecodeString(strings.ReplaceAll(value, "-", ""))
	if err != nil || len(raw) != 16 {
		return "", ErrInvalid
	}
	var nonzero byte
	for _, b := range raw {
		nonzero |= b
	}
	if nonzero == 0 {
		return "", ErrInvalid
	}
	return ID(value), nil
}
func (value ID) Valid() bool    { _, err := Parse(string(value)); return err == nil }
func (value ID) String() string { return string(value) }

type Generator interface{ NewID() (ID, error) }
type Random struct{}

func (Random) NewID() (ID, error) { return generate(rand.Reader) }

func generate(source io.Reader) (ID, error) {
	var raw [16]byte
	if _, err := io.ReadFull(source, raw[:]); err != nil {
		return "", err
	}
	raw[6] = (raw[6] & 0x0f) | 0x40
	raw[8] = (raw[8] & 0x3f) | 0x80
	encoded := hex.EncodeToString(raw[:])
	return ID(encoded[:8] + "-" + encoded[8:12] + "-" + encoded[12:16] + "-" + encoded[16:20] + "-" + encoded[20:]), nil
}
