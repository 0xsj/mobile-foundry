package tests

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/account"
	"github.com/0xsj/mobile-foundry/backend/internal/audit"
	"github.com/0xsj/mobile-foundry/backend/internal/files"
	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/memory"
	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/objects"
	pg "github.com/0xsj/mobile-foundry/backend/internal/files/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/internal/identity"
	"github.com/0xsj/mobile-foundry/backend/internal/server"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"
	"time"
)

func fileSpec(data []byte) domain.Spec {
	hash := sha256.Sum256(data)
	return domain.Spec{Name: "photo.png", ContentType: "image/png", ByteSize: int64(len(data)), SHA256: hex.EncodeToString(hash[:])}
}

type fileAccess bool

func (a fileAccess) Allowed(ctx context.Context, actor, account id.ID) (bool, error) {
	return bool(a), ctx.Err()
}
func filesConformance(t *testing.T, s app.Store) {
	t.Helper()
	ctx := context.Background()
	scope := app.Scope{Actor: randomID(t), Account: randomID(t)}
	now := time.Now()
	create := app.Command{Action: app.Request, Key: "create.1", Spec: fileSpec([]byte("opaque content"))}
	var group sync.WaitGroup
	results := make(chan app.Receipt, 12)
	failures := make(chan error, 12)
	for range 12 {
		candidate := randomID(t)
		group.Go(func() {
			r, err := s.Apply(ctx, scope, create, candidate, now, events.Metadata{})
			results <- r
			failures <- err
		})
	}
	group.Wait()
	close(results)
	close(failures)
	for err := range failures {
		if err != nil {
			t.Fatal(err)
		}
	}
	var first app.Receipt
	for r := range results {
		if first.Key != "" && r != first {
			t.Fatal("replay changed")
		}
		first = r
	}
	rows, err := s.List(ctx, scope.Account, "", 101)
	if err != nil || len(rows) != 1 {
		t.Fatal("duplicate creation", err)
	}
	altered := create
	altered.Spec.Name = "different"
	if _, err := s.Apply(ctx, scope, altered, randomID(t), now, events.Metadata{}); !errors.Is(err, app.ErrKey) {
		t.Fatal("conflicting key", err)
	}
	complete := app.Command{Action: app.Complete, Key: "complete.1", FileID: first.File.ID, Expected: 1}
	done, err := s.Apply(ctx, scope, complete, "", now, events.Metadata{})
	if err != nil || done.File.Status != domain.Available || done.File.Version != 2 {
		t.Fatal("completion", err)
	}
	stale := app.Command{Action: app.Delete, Key: "delete.stale", FileID: first.File.ID, Expected: 1}
	if _, err := s.Apply(ctx, scope, stale, "", now, events.Metadata{}); !errors.Is(err, app.ErrConflict) {
		t.Fatal("CAS", err)
	}
	if _, found, _ := s.Receipt(ctx, scope, stale.Key); found {
		t.Fatal("failed write got receipt")
	}
	remove := app.Command{Action: app.Delete, Key: "delete.1", FileID: first.File.ID, Expected: 2}
	deleted, err := s.Apply(ctx, scope, remove, "", now, events.Metadata{})
	if err != nil || deleted.File.Status != domain.Deleted {
		t.Fatal(err)
	}
	for _, c := range []app.Command{create, complete, remove} {
		if _, err := s.Apply(ctx, scope, c, randomID(t), now, events.Metadata{}); err != nil {
			t.Fatal("historical replay", err)
		}
	}
	rows, err = s.List(ctx, scope.Account, "", 101)
	if err != nil || len(rows) != 0 {
		t.Fatal("deleted file listed")
	}
	if _, found, _ := s.Receipt(ctx, app.Scope{Actor: randomID(t), Account: scope.Account}, create.Key); found {
		t.Fatal("foreign receipt")
	}
	if _, found, _ := s.Get(ctx, randomID(t), first.File.ID); found {
		t.Fatal("foreign file")
	}
	canceled, cancel := context.WithCancel(ctx)
	cancel()
	c := create
	c.Key = "canceled"
	if _, err := s.Apply(canceled, scope, c, randomID(t), now, events.Metadata{}); !errors.Is(err, context.Canceled) {
		t.Fatal("cancellation", err)
	}
	// A concurrent completion/deletion at the same version admits one transition.
	fresh := create
	fresh.Key = "race.create"
	seed, err := s.Apply(ctx, scope, fresh, randomID(t), now, events.Metadata{})
	if err != nil {
		t.Fatal(err)
	}
	race := make(chan error, 2)
	for _, action := range []string{app.Complete, app.Delete} {
		c := app.Command{Action: action, Key: "race." + action, FileID: seed.File.ID, Expected: 1}
		group.Go(func() { _, err := s.Apply(ctx, scope, c, "", now, events.Metadata{}); race <- err })
	}
	group.Wait()
	wins := 0
	for range 2 {
		err := <-race
		if err == nil {
			wins++
		} else if !errors.Is(err, app.ErrConflict) && !errors.Is(err, app.ErrMissing) {
			t.Fatal(err)
		}
	}
	if wins != 1 {
		t.Fatal("race winners", wins)
	}
}
func TestFilesProviderConformance(t *testing.T) {
	t.Run("memory", func(t *testing.T) { filesConformance(t, memory.New(id.Random{})) })
	if url := os.Getenv("TEST_POSTGRES_URL"); url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		t.Run("postgres", func(t *testing.T) {
			pool, err := storage.Open(context.Background(), storage.Config{URL: url})
			if err != nil {
				t.Fatal(err)
			}
			defer pool.Close()
			s, err := pg.New(pool, id.Random{})
			if err != nil {
				t.Fatal(err)
			}
			filesConformance(t, s)
			// Receipt finalization and outbox failure each roll back all metadata.
			scope := app.Scope{Actor: randomID(t), Account: randomID(t)}
			c := app.Command{Action: app.Request, Key: "rollback", Spec: fileSpec([]byte("rollback"))}
			for _, sql := range []string{"ALTER TABLE files.receipts ADD CONSTRAINT fail_files_receipt CHECK(result IS NULL) NOT VALID", "ALTER TABLE files.outbox ADD CONSTRAINT fail_files_outbox CHECK(false) NOT VALID"} {
				if _, err := pool.Exec(context.Background(), sql); err != nil {
					t.Fatal(err)
				}
				if _, err := s.Apply(context.Background(), scope, c, randomID(t), time.Now(), events.Metadata{}); err == nil {
					t.Fatal("forced write committed")
				}
				rows, err := s.List(context.Background(), scope.Account, "", 10)
				if err != nil || len(rows) != 0 {
					t.Fatal("partial state", err)
				}
				if _, found, _ := s.Receipt(context.Background(), scope, c.Key); found {
					t.Fatal("partial receipt")
				}
				table, name := "files.receipts", "fail_files_receipt"
				if strings.Contains(sql, "files.outbox") {
					table, name = "files.outbox", "fail_files_outbox"
				}
				if _, err := pool.Exec(context.Background(), "ALTER TABLE "+table+" DROP CONSTRAINT "+name); err != nil {
					t.Fatal(err)
				}
			}
		})
	}
}
func TestFileObjectAdapters(t *testing.T) {
	ctx := context.Background()
	data := []byte("opaque immutable bytes")
	f, err := domain.New(randomID(t), randomID(t), randomID(t), fileSpec(data), time.Now())
	if err != nil {
		t.Fatal(err)
	}
	s := f.Snapshot()
	root := t.TempDir()
	directory, err := objects.NewDirectory(root)
	if err != nil {
		t.Fatal(err)
	}
	for name, adapter := range map[string]app.Objects{"memory": objects.NewMemory(), "directory": directory} {
		t.Run(name, func(t *testing.T) {
			if err := adapter.Put(ctx, s, []byte("wrong")); !errors.Is(err, app.ErrContent) {
				t.Fatal(err)
			}
			var group sync.WaitGroup
			errs := make(chan error, 8)
			for range 8 {
				group.Go(func() { errs <- adapter.Put(ctx, s, data) })
			}
			group.Wait()
			for range 8 {
				if err := <-errs; err != nil {
					t.Fatal(err)
				}
			}
			got, err := adapter.Get(ctx, s)
			if err != nil || !bytes.Equal(data, got) {
				t.Fatal(err)
			}
			got[0] ^= 1
			again, _ := adapter.Get(ctx, s)
			if !bytes.Equal(data, again) {
				t.Fatal("aliased bytes")
			}
			if name == "directory" {
				reopened, err := objects.NewDirectory(root)
				if err != nil {
					t.Fatal(err)
				}
				if got, err := reopened.Get(ctx, s); err != nil || !bytes.Equal(got, data) {
					t.Fatal("reopen", err)
				}
			}
			if err := adapter.Delete(ctx, s); err != nil {
				t.Fatal(err)
			}
			if err := adapter.Delete(ctx, s); err != nil {
				t.Fatal(err)
			}
			if _, err := adapter.Get(ctx, s); !errors.Is(err, app.ErrObject) {
				t.Fatal(err)
			}
		})
	}
}
func TestFilesApplicationRecoveryAndAccess(t *testing.T) {
	ctx := context.Background()
	s := memory.New(id.Random{})
	blob := objects.NewMemory()
	scope := app.Scope{Actor: randomID(t), Account: randomID(t)}
	data := []byte("photo payload")
	request := command.RequestUpload{Store: s, Access: fileAccess(true), IDs: id.Random{}, Clock: clock.System{}}
	in := command.RequestUploadInput{Scope: scope, Spec: fileSpec(data), Key: "request"}
	r, err := request.Handle(ctx, in)
	if err != nil {
		t.Fatal(err)
	}
	complete := command.CompleteUpload{Store: s, Objects: blob, Access: fileAccess(true), Clock: clock.System{}}
	ci := command.CompleteUploadInput{Scope: scope, FileID: r.File.ID, Expected: 1, Key: "complete"}
	if _, err := complete.Handle(ctx, ci); !errors.Is(err, app.ErrObject) {
		t.Fatal("completed without bytes", err)
	}
	upload := command.UploadContent{Store: s, Objects: blob, Access: fileAccess(true)}
	if err := upload.Handle(ctx, command.UploadContentInput{Scope: scope, FileID: r.File.ID, ContentType: "image/png", Bytes: data}); err != nil {
		t.Fatal(err)
	}
	if _, err := complete.Handle(ctx, ci); err != nil {
		t.Fatal(err)
	}
	remove := command.DeleteFile{Store: s, Objects: blob, Access: fileAccess(true), Clock: clock.System{}}
	if _, err := remove.Handle(ctx, command.DeleteFileInput{Scope: scope, FileID: r.File.ID, Expected: 2, Key: "delete"}); err != nil {
		t.Fatal(err)
	}
	if _, err := complete.Handle(ctx, ci); err != nil {
		t.Fatal("completion receipt unavailable after delete", err)
	}
	if err := upload.Handle(ctx, command.UploadContentInput{Scope: scope, FileID: r.File.ID, ContentType: "image/png", Bytes: data}); !errors.Is(err, app.ErrMissing) {
		t.Fatal("resurrected deletion", err)
	}
	request.Access = fileAccess(false)
	if _, err := request.Handle(ctx, in); !errors.Is(err, app.ErrForbidden) {
		t.Fatal("replay bypassed access", err)
	}
}
func filesHTTP(t *testing.T) harness {
	registry := capability.NewRegistry()
	runtime := module.Runtime{Clock: clock.System{}, IDs: id.Random{}, Capabilities: registry, Events: events.NewMemory(), Principals: identity.CredentialResolver(registry.Client("api"))}
	units, err := module.Assemble(runtime, account.Build, audit.Build, identity.Build, files.Build)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = units.Close() })
	app, err := server.New(config.Runtime{ShutdownTimeout: time.Second}, slog.New(slog.NewJSONHandler(io.Discard, nil)), units)
	if err != nil {
		t.Fatal(err)
	}
	api := httptest.NewServer(app.Handler())
	t.Cleanup(api.Close)
	return harness{t, api.URL}
}
func TestFilesAuthenticatedHTTPAndAudit(t *testing.T) {
	h := filesHTTP(t)
	for _, name := range []string{"filealex", "filesam"} {
		status, _, wire := h.request("POST", "/v1/identity/register", "", `{"login":"`+name+`","display_name":"Person","password":"private test passphrase"}`, nil)
		if status != 201 {
			t.Fatal(status, string(wire))
		}
	}
	alex, sam := login(t, h, "filealex"), login(t, h, "filesam")
	_, _, wire := h.request("POST", "/v1/accounts", alex.Access, `{"name":"Media"}`, nil)
	var account struct {
		ID string `json:"id"`
	}
	if json.Unmarshal(wire, &account) != nil || account.ID == "" {
		t.Fatal(string(wire))
	}
	path := "/v1/accounts/" + account.ID + "/files"
	data := []byte("binary\x00payload")
	spec := fileSpec(data)
	body, _ := json.Marshal(map[string]any{"name": spec.Name, "content_type": spec.ContentType, "byte_size": spec.ByteSize, "sha256": spec.SHA256})
	status, headers, wire := h.request("POST", path+"/uploads", alex.Access, string(body), map[string]string{"Idempotency-Key": "photo.1"})
	if status != 201 || headers.Get("ETag") != `"1"` {
		t.Fatal(status, string(wire))
	}
	var r struct {
		File struct {
			ID string `json:"id"`
		} `json:"file"`
	}
	_ = json.Unmarshal(wire, &r)
	target := path + "/" + r.File.ID
	raw := func(method, token string, payload []byte) (int, []byte) {
		request, _ := http.NewRequest(method, h.address+target+"/content", bytes.NewReader(payload))
		request.Header.Set("Authorization", "Bearer "+token)
		if method == "PUT" {
			request.Header.Set("Content-Type", "image/png")
		}
		response, err := http.DefaultClient.Do(request)
		if err != nil {
			t.Fatal(err)
		}
		defer response.Body.Close()
		wire, _ := io.ReadAll(response.Body)
		return response.StatusCode, wire
	}
	if status, _ := raw("PUT", sam.Access, data); status != 403 {
		t.Fatal("foreign upload", status)
	}
	if status, _ := raw("GET", alex.Access, nil); status != 409 {
		t.Fatal("pending download", status)
	}
	if status, _ := raw("PUT", alex.Access, []byte("wrong")); status != 400 {
		t.Fatal("wrong content", status)
	}
	if status, wire := raw("PUT", alex.Access, data); status != 200 {
		t.Fatal(status, string(wire))
	}
	status, _, wire = h.request("POST", target+"/complete", alex.Access, "", map[string]string{"If-Match": `"1"`, "Idempotency-Key": "complete.1"})
	if status != 200 {
		t.Fatal(status, string(wire))
	}
	if status, wire := raw("GET", alex.Access, nil); status != 200 || !bytes.Equal(wire, data) {
		t.Fatal("download", status, string(wire))
	}
	if status, _ := raw("GET", sam.Access, nil); status != 403 {
		t.Fatal("foreign download", status)
	}
	status, _, wire = h.request("GET", "/v1/receipts/files/complete.1?account_id="+account.ID, alex.Access, "", nil)
	if status != 200 {
		t.Fatal("receipt", status, string(wire))
	}
	status, _, wire = h.request("DELETE", target, alex.Access, "", map[string]string{"If-Match": `"2"`, "Idempotency-Key": "delete.1"})
	if status != 200 {
		t.Fatal(status, string(wire))
	}
	if status, _ := raw("GET", alex.Access, nil); status != 404 {
		t.Fatal("deleted download", status)
	}
	deadline := time.Now().Add(3 * time.Second)
	for {
		_, _, wire = h.request("GET", "/v1/accounts/"+account.ID+"/audit", alex.Access, "", nil)
		var page struct {
			Entries []struct {
				Action string `json:"action"`
				FileID string `json:"file_id"`
			} `json:"entries"`
		}
		_ = json.Unmarshal(wire, &page)
		seen := 0
		for _, e := range page.Entries {
			if strings.HasPrefix(e.Action, "files.") {
				if e.FileID != r.File.ID {
					t.Fatal("wrong audit subject")
				}
				seen++
			}
		}
		if seen == 3 {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("files audit missing", string(wire))
		}
		time.Sleep(20 * time.Millisecond)
	}
}

type failFileCleanup struct {
	app.Objects
	failed bool
}

func (s *failFileCleanup) Delete(ctx context.Context, file domain.Snapshot) error {
	if !s.failed {
		s.failed = true
		return app.ErrStorage
	}
	return s.Objects.Delete(ctx, file)
}
func TestFilesDeletionReceiptSurvivesCleanupFailure(t *testing.T) {
	ctx := context.Background()
	store := memory.New(id.Random{})
	base := objects.NewMemory()
	scope := app.Scope{Actor: randomID(t), Account: randomID(t)}
	data := []byte("private pending bytes")
	request := command.RequestUpload{Store: store, Access: fileAccess(true), IDs: id.Random{}, Clock: clock.System{}}
	r, err := request.Handle(ctx, command.RequestUploadInput{Scope: scope, Spec: fileSpec(data), Key: "cleanup.request"})
	if err != nil {
		t.Fatal(err)
	}
	if err := base.Put(ctx, r.File, data); err != nil {
		t.Fatal(err)
	}
	failing := &failFileCleanup{Objects: base}
	handler := command.DeleteFile{Store: store, Objects: failing, Access: fileAccess(true), Clock: clock.System{}}
	input := command.DeleteFileInput{Scope: scope, FileID: r.File.ID, Expected: 1, Key: "cleanup.delete"}
	if _, err := handler.Handle(ctx, input); !errors.Is(err, app.ErrStorage) {
		t.Fatal("cleanup failure hidden", err)
	}
	receipt, found, err := store.Receipt(ctx, scope, input.Key)
	if err != nil || !found || receipt.File.Status != domain.Deleted {
		t.Fatal("logical deletion rolled back", err)
	}
	complete := command.CompleteUpload{Store: store, Objects: base, Access: fileAccess(true), Clock: clock.System{}}
	if _, err := complete.Handle(ctx, command.CompleteUploadInput{Scope: scope, FileID: r.File.ID, Expected: 1, Key: "late.complete"}); !errors.Is(err, app.ErrMissing) {
		t.Fatal("deleted upload requested bytes", err)
	}
	if got, err := handler.Handle(ctx, input); err != nil || got != receipt {
		t.Fatal("same-key cleanup recovery", err)
	}
	if _, err := base.Get(ctx, r.File); !errors.Is(err, app.ErrObject) {
		t.Fatal("bytes not cleaned", err)
	}
}
