// Package records owns the v1 persistence/fingerprint encoding shared by stores.
package records

import (
	"crypto/sha256"
	"encoding/json"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
)

func Fingerprint(c app.Command) [32]byte {
	b, _ := json.Marshal(struct {
		Action      string `json:"action"`
		FileID      string `json:"file_id"`
		Name        string `json:"name"`
		ContentType string `json:"content_type"`
		ByteSize    int64  `json:"byte_size"`
		SHA256      string `json:"sha256"`
		Expected    uint64 `json:"expected_version"`
	}{c.Action, c.FileID.String(), c.Spec.Name, c.Spec.ContentType, c.Spec.ByteSize, c.Spec.SHA256, c.Expected})
	return sha256.Sum256(append([]byte("files.command.v1\n"), b...))
}
func Decode(wire []byte) (domain.File, error) {
	var s domain.Snapshot
	if err := json.Unmarshal(wire, &s); err != nil {
		return domain.File{}, domain.ErrState.WithCause(err)
	}
	return domain.Restore(s)
}
func DecodeReceipt(wire []byte) (app.Receipt, error) {
	var r app.Receipt
	if err := json.Unmarshal(wire, &r); err != nil {
		return r, domain.ErrState.WithCause(err)
	}
	if _, err := domain.Restore(r.File); err != nil {
		return r, err
	}
	validAction := r.Action == app.Request && r.File.Status == domain.Pending || r.Action == app.Complete && r.File.Status == domain.Available || r.Action == app.Delete && r.File.Status == domain.Deleted
	if !r.Actor.Valid() || !app.ValidKey(r.Key) || !validAction {
		return r, domain.ErrState
	}
	return r, nil
}
