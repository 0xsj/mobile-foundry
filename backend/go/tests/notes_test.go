package tests

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/infra/memory"
	notePG "github.com/0xsj/mobile-foundry/backend/internal/notes/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"os"
	"sync"
	"testing"
)

func notesConformance(t *testing.T, s app.Store) {
	ctx := context.Background()
	account, actor := randomID(t), randomID(t)
	input := domain.Write{CommandID: randomID(t), Action: domain.Create, Title: "  Café 🧱\x00\n  "}
	var group sync.WaitGroup
	results := make(chan domain.Receipt, 12)
	failures := make(chan error, 12)
	for range 12 {
		candidate := randomID(t)
		group.Go(func() { r, err := s.Apply(ctx, account, actor, input, candidate); results <- r; failures <- err })
	}
	group.Wait()
	close(results)
	close(failures)
	for err := range failures {
		if err != nil {
			t.Fatal(err)
		}
	}
	var receipt domain.Receipt
	for r := range results {
		if receipt.CommandID != "" && receipt != r {
			t.Fatal("concurrent replay returned another result")
		}
		receipt = r
	}
	rows, err := s.List(ctx, account, "", 100)
	if err != nil || len(rows) != 1 || rows[0].Title != input.Title || rows[0].Version != 1 {
		t.Fatal("duplicate create or title loss", err)
	}
	altered := input
	altered.Title = "other"
	if _, err := s.Apply(ctx, account, actor, altered, randomID(t)); !errors.Is(err, domain.ErrKey) {
		t.Fatal("key reuse accepted", err)
	}
	changed := domain.Write{CommandID: randomID(t), Action: domain.Update, NoteID: receipt.NoteID, Title: "Changed", Expected: 1}
	edited, err := s.Apply(ctx, account, actor, changed, "")
	if err != nil || edited.Version != 2 {
		t.Fatal(err)
	}
	races := make(chan error, 12)
	wins := 0
	for range 12 {
		w := domain.Write{CommandID: randomID(t), Action: domain.Update, NoteID: receipt.NoteID, Title: "Race", Expected: 2}
		group.Go(func() { _, err := s.Apply(ctx, account, actor, w, ""); races <- err })
	}
	group.Wait()
	close(races)
	for err := range races {
		if err == nil {
			wins++
		} else if !errors.Is(err, domain.ErrConflict) {
			t.Fatal("CAS failure", err)
		}
	}
	if wins != 1 {
		t.Fatal("CAS winner count", wins)
	}
	removed := domain.Write{CommandID: randomID(t), Action: domain.Delete, NoteID: receipt.NoteID, Expected: 3}
	deleted, err := s.Apply(ctx, account, actor, removed, "")
	if err != nil || deleted.Version != 4 {
		t.Fatal(err)
	}
	for _, w := range []domain.Write{input, changed, removed} {
		r, err := s.Apply(ctx, account, actor, w, randomID(t))
		if err != nil {
			t.Fatal("replay after deletion failed", err)
		}
		saved, found, err := s.Receipt(ctx, account, actor, w.CommandID)
		if err != nil || !found || saved != r {
			t.Fatal("receipt missing", err)
		}
	}
	if _, found, err := s.Get(ctx, account, receipt.NoteID); err != nil || found {
		t.Fatal("delete replay recreated record")
	}
	if _, found, err := s.Receipt(ctx, account, randomID(t), input.CommandID); err != nil || found {
		t.Fatal("cross-principal receipt leak")
	}
	if _, found, err := s.Receipt(ctx, randomID(t), actor, input.CommandID); err != nil || found {
		t.Fatal("cross-account receipt leak")
	}
	stale := domain.Write{CommandID: randomID(t), Action: domain.Update, NoteID: receipt.NoteID, Title: "Lost", Expected: 3}
	if _, err := s.Apply(ctx, account, actor, stale, ""); !errors.Is(err, domain.ErrMissing) {
		t.Fatal(err)
	}
	if _, found, _ := s.Receipt(ctx, account, actor, stale.CommandID); found {
		t.Fatal("rejected write got commit receipt")
	}
	cancelled, cancel := context.WithCancel(ctx)
	cancel()
	fresh := domain.Write{CommandID: randomID(t), Action: domain.Create, Title: "Cancelled"}
	if _, err := s.Apply(cancelled, account, actor, fresh, randomID(t)); !errors.Is(err, context.Canceled) {
		t.Fatal(err)
	}
	if _, found, _ := s.Receipt(ctx, account, actor, fresh.CommandID); found {
		t.Fatal("cancelled write committed")
	}
}
func TestNotesProviderConformance(t *testing.T) {
	t.Run("memory", func(t *testing.T) { notesConformance(t, memory.New()) })
	if url := os.Getenv("TEST_POSTGRES_URL"); url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		t.Run("postgres", func(t *testing.T) {
			ctx := context.Background()
			pool, err := storage.Open(ctx, storage.Config{URL: url})
			if err != nil {
				t.Fatal(err)
			}
			defer pool.Close()
			s := notePG.New(pool)
			notesConformance(t, s)
			// Force receipt finalization to fail after the INSERT of the note, proving rollback.
			if _, err := pool.Exec(ctx, "ALTER TABLE notes.receipts ADD CONSTRAINT fail_receipt CHECK(result IS NULL) NOT VALID"); err != nil {
				t.Fatal(err)
			}
			defer pool.Exec(ctx, "ALTER TABLE notes.receipts DROP CONSTRAINT fail_receipt")
			account, actor, note := randomID(t), randomID(t), randomID(t)
			w := domain.Write{CommandID: randomID(t), Action: domain.Create, Title: "Atomic"}
			if _, err := s.Apply(ctx, account, actor, w, note); err == nil {
				t.Fatal("injected receipt failure ignored")
			}
			if _, found, err := s.Get(ctx, account, note); err != nil || found {
				t.Fatal("partial note commit", err)
			}
			if _, found, err := s.Receipt(ctx, account, actor, w.CommandID); err != nil || found {
				t.Fatal("partial receipt claim", err)
			}
			// Even an external statement cannot commit a dangling claim.
			if _, err := pool.Exec(ctx, `INSERT INTO notes.receipts(account_id,principal_id,command_id,fingerprint) VALUES($1,$2,$3,$4)`, account.String(), actor.String(), randomID(t).String(), make([]byte, 32)); err == nil {
				t.Fatal("incomplete receipt committed")
			}
		})
	}
}
func TestNotesHTTPReceiptVersionsAndScope(t *testing.T) {
	h := setup(t, events.NewMemory())
	account, _ := h.create("Notes")
	path := "/v1/accounts/" + account + "/notes"
	key := randomID(t).String()
	headers := map[string]string{"Idempotency-Key": key}
	status, meta, wire := h.request("POST", path, "owner", `{"title":"Remote"}`, headers)
	var created domain.Receipt
	if status != 201 || json.Unmarshal(wire, &created) != nil || !created.Valid() || meta.Get("ETag") != `"1"` {
		t.Fatal(status, string(wire))
	}
	status, _, replay := h.request("POST", path, "owner", `{"title":"Remote"}`, headers)
	if status != 201 || string(replay) != string(wire) {
		t.Fatal("replay mismatch", status)
	}
	if status, _, _ := h.request("POST", path, "owner", `{"title":"Changed"}`, headers); status != 409 {
		t.Fatal("key conflict admitted", status)
	}
	notePath := path + "/" + created.NoteID.String()
	update := map[string]string{"Idempotency-Key": randomID(t).String(), "If-Match": `"1"`}
	if status, _, _ := h.request("PATCH", notePath, "owner", `{"title":"Edited"}`, update); status != 200 {
		t.Fatal(status)
	}
	if status, _, _ := h.request("PATCH", notePath, "owner", `{"title":"Stale"}`, map[string]string{"Idempotency-Key": randomID(t).String(), "If-Match": `"1"`}); status != 409 {
		t.Fatal(status)
	}
	if status, _, _ := h.request("DELETE", notePath, "owner", "", map[string]string{"Idempotency-Key": randomID(t).String(), "If-Match": `"2"`}); status != 200 {
		t.Fatal(status)
	}
	status, _, wire = h.request("GET", meta.Get("Location"), "owner", "", nil)
	var receipt domain.Receipt
	if status != 200 || json.Unmarshal(wire, &receipt) != nil || receipt != created {
		t.Fatal("historical receipt lost")
	}
	for _, route := range []string{path, notePath, meta.Get("Location")} {
		if status, _, _ := h.request("GET", route, "other", "", nil); status != 403 {
			t.Fatal("foreign scope admitted", status)
		}
		if status, _, _ := h.request("GET", route, "", "", nil); status != 401 {
			t.Fatal("anonymous scope admitted", status)
		}
	}
	otherAccount, _ := h.create("Empty")
	if status, _, _ := h.request("GET", "/v1/accounts/"+otherAccount+"/note-receipts/"+key, "owner", "", nil); status != 404 {
		t.Fatal("cross-account receipt leak")
	}
	if status, _, _ := h.request("POST", path, "owner", `{"title":"No key"}`, nil); status != 400 {
		t.Fatal("missing command admitted")
	}
	if status, _, _ := h.request("PATCH", notePath, "owner", `{"title":"No version"}`, headers); status != 428 {
		t.Fatal("missing version admitted")
	}
	for _, q := range []string{"?limit=0", "?limit=01", "?limit=101", "?limit=1&limit=2", "?after=bad", "?spoof=x"} {
		if status, _, _ := h.request("GET", path+q, "owner", "", nil); status != 400 {
			t.Fatal("bad page admitted", q, status)
		}
	}
	for range 3 {
		if status, _, _ := h.request("POST", path, "owner", `{"title":"Page"}`, map[string]string{"Idempotency-Key": randomID(t).String()}); status != 201 {
			t.Fatal(status)
		}
	}
	status, _, wire = h.request("GET", path+"?limit=2", "owner", "", nil)
	var page struct {
		Notes []domain.Note
		Next  *string `json:"next_after"`
	}
	if status != 200 || json.Unmarshal(wire, &page) != nil || len(page.Notes) != 2 || page.Next == nil || *page.Next != page.Notes[1].ID.String() {
		t.Fatal("page invalid", string(wire))
	}
	status, _, wire = h.request("GET", path+"?limit=2&after="+*page.Next, "owner", "", nil)
	if status != 200 || json.Unmarshal(wire, &page) != nil || len(page.Notes) != 1 || page.Next != nil {
		t.Fatal("continuation invalid")
	}
}
