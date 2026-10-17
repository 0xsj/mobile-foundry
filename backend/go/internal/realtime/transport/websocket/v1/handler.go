package v1

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/realtime/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
	"net/http"
	"sync"
	"time"
)

// Handler admits native bearer headers only; no credentials/cookies/query tickets.
// Ready always requests an HTTP refetch. Hints are lossy and carry no resource data.
type Handler struct {
	Subscribe   query.Subscribe
	Principals  principal.Resolver
	Recheck     time.Duration
	Lifetime    time.Duration
	mu          sync.Mutex
	stopping    bool
	connections map[*websocket.Conn]struct{}
	wg          sync.WaitGroup
}

func (h *Handler) Register(mux *http.ServeMux) {
	mux.Handle("/v1/accounts/{account}/realtime", httpx.Methods(map[string]httpx.Endpoint{"GET": h.serve}))
}
func (h *Handler) Stop() error {
	h.mu.Lock()
	h.stopping = true
	for c := range h.connections {
		_ = c.CloseNow()
	}
	h.mu.Unlock()
	h.wg.Wait()
	return nil
}
func (h *Handler) serve(w http.ResponseWriter, r *http.Request) error {
	if r.Method != "GET" || r.URL.RawQuery != "" || len(r.Header.Values("Origin")) != 0 || len(r.Header.Values("Cookie")) != 0 {
		return httpx.ErrInvalidJSON
	}
	account, err := id.Parse(r.PathValue("account"))
	if err != nil {
		return err
	}
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	feed, release, err := h.Subscribe.Handle(r.Context(), actor.ID, account)
	if err != nil {
		return err
	}
	defer release()
	h.mu.Lock()
	if h.stopping {
		h.mu.Unlock()
		return principal.ErrRequired
	}
	h.wg.Add(1)
	h.mu.Unlock()
	defer h.wg.Done()
	// The ordinary HTTP boundary has a 15-second budget. The socket owns a bounded
	// independent lifetime after admission and explicitly revalidates the same bearer.
	_ = http.NewResponseController(w).SetWriteDeadline(time.Now().Add(3 * time.Second))
	c, err := websocket.Accept(w, r, &websocket.AcceptOptions{CompressionMode: websocket.CompressionDisabled})
	if err != nil {
		return nil
	}
	_ = http.NewResponseController(w).SetWriteDeadline(time.Time{})
	h.mu.Lock()
	if h.stopping {
		h.mu.Unlock()
		_ = c.CloseNow()
		return nil
	}
	if h.connections == nil {
		h.connections = make(map[*websocket.Conn]struct{})
	}
	h.connections[c] = struct{}{}
	h.mu.Unlock()
	defer func() { _ = c.CloseNow(); h.mu.Lock(); delete(h.connections, c); h.mu.Unlock() }()
	c.SetReadLimit(1024)
	lifetime := h.Lifetime
	if lifetime <= 0 {
		lifetime = 10 * time.Minute
	}
	ctx, cancel := context.WithTimeout(context.WithoutCancel(r.Context()), lifetime)
	defer cancel()
	ctx = c.CloseRead(ctx)
	interval := h.Recheck
	if interval <= 0 {
		interval = 5 * time.Second
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	check := func() error {
		budget, done := context.WithTimeout(ctx, 2*time.Second)
		defer done()
		// Preserve exact bearer/session identity; renewal/revocation expires this connection.
		current, e := httpx.RequirePrincipal(r.WithContext(budget), h.Principals)
		if e != nil {
			return e
		}
		if current.ID != actor.ID || current.SessionID != actor.SessionID {
			return principal.ErrRequired
		}
		return h.Subscribe.Check(budget, actor.ID, account)
	}
	write := func(kind string) error {
		if e := check(); e != nil {
			return e
		}
		budget, done := context.WithTimeout(ctx, 3*time.Second)
		defer done()
		return wsjson.Write(budget, c, struct {
			Type     string `json:"type"`
			Resource string `json:"resource"`
			Account  id.ID  `json:"account_id"`
			Refetch  bool   `json:"refetch"`
		}{kind, "notes", account, true})
	}
	if write("ready") != nil {
		return nil
	}
	for {
		select {
		case <-ctx.Done():
			return nil
		case _, open := <-feed:
			if !open || write("changed") != nil {
				return nil
			}
		case <-ticker.C:
			if check() != nil {
				return nil
			}
			budget, done := context.WithTimeout(ctx, 3*time.Second)
			err := c.Ping(budget)
			done()
			if err != nil {
				return nil
			}
		}
	}
}
