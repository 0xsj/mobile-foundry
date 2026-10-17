// Package objects implements immutable content storage without metadata ownership.
package objects

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"os"
	"path/filepath"
	"sync"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
)

func validate(s domain.Snapshot, data []byte) error {
	if _, err := domain.Restore(s); err != nil {
		return err
	}
	hash := sha256.Sum256(data)
	if int64(len(data)) != s.Spec.ByteSize || hex.EncodeToString(hash[:]) != s.Spec.SHA256 {
		return app.ErrContent
	}
	return nil
}
func key(s domain.Snapshot) string { return s.AccountID.String() + "-" + s.ID.String() }

type Memory struct {
	mu    sync.Mutex
	bytes map[string][]byte
	size  int64
}

func NewMemory() *Memory { return &Memory{bytes: map[string][]byte{}} }
func (m *Memory) Put(ctx context.Context, s domain.Snapshot, data []byte) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := validate(s, data); err != nil {
		return err
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	if prior, ok := m.bytes[key(s)]; ok {
		return validate(s, prior)
	}
	if m.size+int64(len(data)) > 64*1024*1024 {
		return app.ErrStorage
	}
	m.bytes[key(s)] = append([]byte(nil), data...)
	m.size += int64(len(data))
	return nil
}
func (m *Memory) Get(ctx context.Context, s domain.Snapshot) ([]byte, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	data, found := m.bytes[key(s)]
	if !found {
		return nil, app.ErrObject
	}
	if err := validate(s, data); err != nil {
		return nil, domain.ErrState.WithCause(err)
	}
	return append([]byte(nil), data...), nil
}
func (m *Memory) Delete(ctx context.Context, s domain.Snapshot) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	m.size -= int64(len(m.bytes[key(s)]))
	delete(m.bytes, key(s))
	return nil
}

// Directory is private application storage. Filenames contain validated IDs only.
// Temp+link publishes one complete immutable object and never replaces an existing one.
type Directory struct{ root string }

func NewDirectory(root string) (*Directory, error) {
	if root == "" {
		return nil, app.ErrInput
	}
	absolute, err := filepath.Abs(root)
	if err != nil {
		return nil, app.ErrStorage.WithCause(err)
	}
	if err = os.MkdirAll(absolute, 0700); err != nil {
		return nil, app.ErrStorage.WithCause(err)
	}
	return &Directory{absolute}, nil
}
func (d *Directory) path(s domain.Snapshot) (string, error) {
	if _, err := domain.Restore(s); err != nil {
		return "", err
	}
	return filepath.Join(d.root, key(s)), nil
}
func (d *Directory) Put(ctx context.Context, s domain.Snapshot, data []byte) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := validate(s, data); err != nil {
		return err
	}
	path, err := d.path(s)
	if err != nil {
		return err
	}
	tmp, err := os.CreateTemp(d.root, ".upload-")
	if err != nil {
		return app.ErrStorage.WithCause(err)
	}
	defer os.Remove(tmp.Name())
	defer tmp.Close()
	if _, err = tmp.Write(data); err != nil {
		return app.ErrStorage.WithCause(err)
	}
	if err = tmp.Sync(); err != nil {
		return app.ErrStorage.WithCause(err)
	}
	if err = tmp.Close(); err != nil {
		return app.ErrStorage.WithCause(err)
	}
	if err = ctx.Err(); err != nil {
		return err
	}
	if err = os.Link(tmp.Name(), path); err != nil {
		if errors.Is(err, os.ErrExist) {
			_, err = d.Get(ctx, s)
			return err
		}
		return app.ErrStorage.WithCause(err)
	}
	return syncDirectory(d.root)
}
func (d *Directory) Get(ctx context.Context, s domain.Snapshot) ([]byte, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	path, err := d.path(s)
	if err != nil {
		return nil, err
	}
	f, err := os.Open(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil, app.ErrObject
	}
	if err != nil {
		return nil, app.ErrStorage.WithCause(err)
	}
	defer f.Close()
	data, err := io.ReadAll(io.LimitReader(f, domain.MaxBytes+1))
	if err != nil {
		return nil, app.ErrStorage.WithCause(err)
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if err := validate(s, data); err != nil {
		return nil, domain.ErrState.WithCause(err)
	}
	return data, nil
}
func (d *Directory) Delete(ctx context.Context, s domain.Snapshot) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	path, err := d.path(s)
	if err != nil {
		return err
	}
	if err = os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
		return app.ErrStorage.WithCause(err)
	}
	return syncDirectory(d.root)
}
func syncDirectory(path string) error {
	f, err := os.Open(path)
	if err != nil {
		return app.ErrStorage.WithCause(err)
	}
	defer f.Close()
	if err = f.Sync(); err != nil {
		return app.ErrStorage.WithCause(err)
	}
	return nil
}
