package tests

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/account/infra/memory"
	accountpg "github.com/0xsj/mobile-foundry/backend/internal/account/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type accountDirectory interface {
	app.Store
	app.Directory
}

func ownedAccount(t *testing.T, owner id.ID, name string) domain.Account {
	t.Helper()
	account, err := domain.New(randomID(t), owner, name, time.Now())
	if err != nil {
		t.Fatal(err)
	}
	return account
}

func directoryConformance(t *testing.T, store accountDirectory) id.ID {
	ctx := context.Background()
	owner := randomID(t)
	ordinary := ownedAccount(t, owner, "Existing workspace")
	fact, _ := ordinary.Fact(domain.Created, owner)
	if err := store.Create(ctx, ordinary, fact, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	other := ownedAccount(t, randomID(t), "Other owner")
	fact, _ = other.Fact(domain.Created, other.Snapshot().OwnerID)
	if err := store.Create(ctx, other, fact, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	type outcome struct {
		account domain.Account
		created bool
		err     error
	}
	results := make(chan outcome, 12)
	var group sync.WaitGroup
	for range 12 {
		candidate := ownedAccount(t, owner, "Personal")
		fact, _ := candidate.Fact(domain.Created, owner)
		group.Go(func() {
			value, created, err := store.Bootstrap(ctx, candidate, fact, events.Metadata{})
			results <- outcome{value, created, err}
		})
	}
	group.Wait()
	close(results)
	wins := 0
	var personal domain.Account
	for result := range results {
		if result.err != nil {
			t.Fatal(result.err)
		}
		if personal.Snapshot().ID != "" && personal.Snapshot() != result.account.Snapshot() {
			t.Fatal("bootstrap returned different winners")
		}
		personal = result.account
		if result.created {
			wins++
		}
	}
	if wins != 1 || personal.Snapshot().ID == ordinary.Snapshot().ID || personal.Snapshot().OwnerID != owner {
		t.Fatal("bootstrap did not own one explicit mapping", wins)
	}
	renamed, err := personal.Rename("Renamed personal", time.Now())
	if err != nil {
		t.Fatal(err)
	}
	fact, _ = renamed.Fact(domain.Renamed, owner)
	if err := store.Rename(ctx, renamed, 1, fact, events.Metadata{}); err != nil {
		t.Fatal(err)
	}
	candidate := ownedAccount(t, owner, "Do not overwrite")
	fact, _ = candidate.Fact(domain.Created, owner)
	repeated, created, err := store.Bootstrap(ctx, candidate, fact, events.Metadata{})
	if err != nil || created || repeated.Snapshot() != renamed.Snapshot() {
		t.Fatal("repeat overwrote the mapped account", err)
	}
	if _, found, err := store.Get(ctx, candidate.Snapshot().ID); err != nil || found {
		t.Fatal("repeat left an orphan account", err)
	}
	page, err := (query.List{Directory: store}).Handle(ctx, query.ListInput{Principal: principal.Principal{ID: owner}, Limit: 1})
	if err != nil || len(page.Accounts) != 1 || !page.Next.Valid() {
		t.Fatal("first owned page failed", err)
	}
	next, err := (query.List{Directory: store}).Handle(ctx, query.ListInput{Principal: principal.Principal{ID: owner}, After: page.Next, Limit: 1})
	if err != nil || len(next.Accounts) != 1 || next.Next != "" || next.Accounts[0].Snapshot().ID <= page.Next {
		t.Fatal("continuation failed", err)
	}
	empty, err := store.List(ctx, randomID(t), "", 10)
	if err != nil || len(empty) != 0 {
		t.Fatal("empty discovery leaked another owner", err)
	}
	cancelled, cancel := context.WithCancel(ctx)
	cancel()
	newOwner := randomID(t)
	newCandidate := ownedAccount(t, newOwner, "Cancelled")
	fact, _ = newCandidate.Fact(domain.Created, newOwner)
	if _, _, err := store.Bootstrap(cancelled, newCandidate, fact, events.Metadata{}); !errors.Is(err, context.Canceled) {
		t.Fatal("bootstrap cancellation ignored", err)
	}
	if _, found, err := store.Get(ctx, newCandidate.Snapshot().ID); err != nil || found {
		t.Fatal("cancelled bootstrap persisted", err)
	}
	return owner
}

func TestAccountDirectoryConformance(t *testing.T) {
	t.Run("memory", func(t *testing.T) {
		store := memory.New(id.Random{})
		owner := directoryConformance(t, store)
		pending, err := store.Pending(context.Background(), 100)
		if err != nil {
			t.Fatal(err)
		}
		n := 0
		for _, message := range pending {
			if message.ActorID == owner {
				n++
			}
		}
		if n != 3 {
			t.Fatal("repeat bootstrap emitted extra facts", n)
		}
	})
	url := os.Getenv("TEST_POSTGRES_URL")
	if url != "" && os.Getenv("TEST_POSTGRES_DISPOSABLE") == "1" {
		t.Run("postgres", func(t *testing.T) {
			pool, err := storage.Open(context.Background(), storage.Config{URL: url})
			if err != nil {
				t.Fatal(err)
			}
			defer pool.Close()
			store, err := accountpg.New(pool, id.Random{})
			if err != nil {
				t.Fatal(err)
			}
			owner := directoryConformance(t, store)
			var n int
			if err := pool.QueryRow(context.Background(), "SELECT count(*) FROM account.outbox o JOIN account.accounts a ON a.id=o.account_id WHERE a.owner_id=$1", owner.String()).Scan(&n); err != nil || n != 3 {
				t.Fatal("bootstrap facts were duplicated", n, err)
			}
		})
	}
}

type directoryPage struct {
	Accounts []struct {
		ID, Name string
		Version  uint64
	}
	Next *string `json:"next_cursor"`
}

func TestAccountBootstrapDiscoveryAndSelectionHTTP(t *testing.T) {
	h := setup(t, events.NewMemory())
	ordinary, _ := h.create("Existing")
	status, headers, wire := h.request("POST", "/v1/accounts/bootstrap", "owner", `{"name":"Personal"}`, nil)
	var personal struct {
		ID, Name string
		Version  uint64
	}
	if status != 201 || json.Unmarshal(wire, &personal) != nil || personal.ID == ordinary || headers.Get("ETag") != `"1"` {
		t.Fatal("bootstrap failed", status, string(wire))
	}
	status, _, wire = h.request("PATCH", "/v1/accounts/"+personal.ID, "owner", `{"name":"Renamed"}`, map[string]string{"If-Match": `"1"`})
	if status != 200 {
		t.Fatal(status, string(wire))
	}
	status, headers, wire = h.request("POST", "/v1/accounts/bootstrap", "owner", `{"name":"Do not overwrite"}`, nil)
	var repeated struct {
		ID, Name string
		Version  uint64
	}
	if status != 200 || json.Unmarshal(wire, &repeated) != nil || repeated.ID != personal.ID || repeated.Name != "Renamed" || repeated.Version != 2 || headers.Get("ETag") != `"2"` {
		t.Fatal("repeat changed account", status, string(wire))
	}
	h.waitFor(personal.ID, 2)
	status, _, wire = h.request("GET", "/v1/accounts?limit=1", "owner", "", nil)
	var page directoryPage
	if status != 200 || json.Unmarshal(wire, &page) != nil || len(page.Accounts) != 1 || page.Next == nil {
		t.Fatal("discovery failed", status, string(wire))
	}
	cursor, firstID := *page.Next, page.Accounts[0].ID
	status, _, wire = h.request("GET", "/v1/accounts?limit=1&cursor="+cursor, "owner", "", nil)
	if status != 200 || json.Unmarshal(wire, &page) != nil || len(page.Accounts) != 1 || page.Next != nil || page.Accounts[0].ID <= firstID {
		t.Fatal("continuation failed", string(wire))
	}
	status, _, wire = h.request("GET", "/v1/accounts", "other", "", nil)
	if status != 200 || json.Unmarshal(wire, &page) != nil || len(page.Accounts) != 0 || page.Next != nil {
		t.Fatal("owner filtering failed", string(wire))
	}
	status, _, wire = h.request("GET", "/v1/accounts?cursor="+cursor, "other", "", nil)
	if status != 400 {
		t.Fatal("cross-owner cursor accepted", status)
	}
	// Selection is the existing guarded lookup, with no global selected state.
	for _, identity := range []string{ordinary, personal.ID} {
		if status, _, _ := h.request("GET", "/v1/accounts/"+identity, "owner", "", nil); status != 200 {
			t.Fatal("owned selection refused", status)
		}
		if status, _, _ := h.request("GET", "/v1/accounts/"+identity, "other", "", nil); status != 403 {
			t.Fatal("foreign selection admitted", status)
		}
	}
	if status, _, _ := h.request("GET", "/v1/accounts/00000000-0000-4000-8000-000000000099", "owner", "", nil); status != 404 {
		t.Fatal("absent selection admitted", status)
	}
	if status, _, body := h.request("HEAD", "/v1/accounts", "owner", "", nil); status != 200 || len(body) != 0 {
		t.Fatal("HEAD list mismatch")
	}
}

func TestDirectoryHTTPAdmission(t *testing.T) {
	h := setup(t, events.NewMemory())
	invalidCursor := base64.RawURLEncoding.EncodeToString([]byte(`{"version":2,"owner_id":"00000000-0000-4000-8000-000000000001","after":"00000000-0000-4000-8000-000000000099"}`))
	for _, sample := range []struct {
		method, path, token, body string
		headers                   map[string]string
		status                    int
	}{
		{"GET", "/v1/accounts", "", "", nil, 401},
		{"GET", "/v1/accounts?limit=0", "owner", "", nil, 400},
		{"GET", "/v1/accounts?limit=101", "owner", "", nil, 400},
		{"GET", "/v1/accounts?limit=01", "owner", "", nil, 400},
		{"GET", "/v1/accounts?limit=1&limit=2", "owner", "", nil, 400},
		{"GET", "/v1/accounts?owner_id=spoof", "owner", "", nil, 400},
		{"GET", "/v1/accounts?cursor=", "owner", "", nil, 400},
		{"GET", "/v1/accounts?cursor=bad", "owner", "", nil, 400},
		{"GET", "/v1/accounts?cursor=" + invalidCursor, "owner", "", nil, 400},
		{"POST", "/v1/accounts/bootstrap", "", `{"name":"Personal"}`, nil, 401},
		{"POST", "/v1/accounts/bootstrap", "owner", `{"name":" "}`, nil, 400},
		{"POST", "/v1/accounts/bootstrap", "owner", `{"name":"X","owner_id":"spoof"}`, nil, 400},
		{"POST", "/v1/accounts/bootstrap?extra=x", "owner", `{"name":"X"}`, nil, 400},
		{"POST", "/v1/accounts/bootstrap", "owner", `{"name":"X"}`, map[string]string{"Idempotency-Key": "unused"}, 400},
		{"GET", "/v1/accounts/bootstrap", "owner", "", nil, 405},
	} {
		status, _, wire := h.request(sample.method, sample.path, sample.token, sample.body, sample.headers)
		if status != sample.status {
			t.Fatalf("%s %s: %d %s", sample.method, sample.path, status, wire)
		}
	}
	status, _, wire := h.request("GET", "/v1/accounts", "owner", "", nil)
	var page directoryPage
	if status != 200 || json.Unmarshal(wire, &page) != nil || len(page.Accounts) != 0 {
		t.Fatal("invalid admission wrote an account")
	}
}
