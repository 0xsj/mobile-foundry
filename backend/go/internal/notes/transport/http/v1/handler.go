package v1

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notes/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"net/http"
	"net/url"
	"strconv"
)

type Handler struct {
	Write      command.Write
	Read       query.Read
	Principals principal.Resolver
}

func (h Handler) Register(mux *http.ServeMux) {
	mux.Handle("/v1/accounts/{accountID}/notes", httpx.Methods(map[string]httpx.Endpoint{"GET": h.list, "POST": h.create}))
	mux.Handle("/v1/accounts/{accountID}/notes/{noteID}", httpx.Methods(map[string]httpx.Endpoint{"GET": h.get, "PATCH": h.update, "DELETE": h.delete}))
	mux.Handle("/v1/accounts/{accountID}/note-receipts/{commandID}", httpx.Methods(map[string]httpx.Endpoint{"GET": h.receipt}))
}
func (h Handler) scope(r *http.Request) (id.ID, id.ID, error) {
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return "", "", err
	}
	account, err := id.Parse(r.PathValue("accountID"))
	return actor.ID, account, err
}
func noQuery(r *http.Request) error {
	if r.URL.RawQuery != "" || r.URL.ForceQuery {
		return domain.ErrWrite
	}
	return nil
}
func (h Handler) mutate(w http.ResponseWriter, r *http.Request, action string) error {
	actor, account, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := noQuery(r); err != nil {
		return err
	}
	key, err := httpx.IdempotencyKey(r)
	if err != nil {
		return err
	}
	commandID, err := id.Parse(key)
	if err != nil {
		return domain.ErrWrite
	}
	input := domain.Write{CommandID: commandID, Action: action}
	if action == domain.Create {
		if len(r.Header.Values("If-Match")) > 0 {
			return httpx.ErrPrecondition
		}
	} else {
		input.NoteID, err = id.Parse(r.PathValue("noteID"))
		if err != nil {
			return err
		}
		input.Expected, err = httpx.RequiredVersion(r)
		if err != nil {
			return err
		}
	}
	if action != domain.Delete {
		var body struct {
			Title *string `json:"title"`
		}
		if err := httpx.DecodeJSON(w, r, &body); err != nil {
			return err
		}
		if body.Title == nil {
			return domain.ErrTitle
		}
		input.Title = *body.Title
	} else {
		// Bodyless delete; refuse content rather than silently ignoring caller input.
		if r.ContentLength != 0 || len(r.TransferEncoding) > 0 {
			return httpx.ErrInvalidJSON
		}
	}
	receipt, err := h.Write.Handle(r.Context(), actor, account, input)
	if err != nil {
		return err
	}
	// Successful replays return identical evidence and status, even after later edits.
	status := http.StatusOK
	if action == domain.Create {
		status = http.StatusCreated
	}
	w.Header().Set("Location", "/v1/accounts/"+account.String()+"/note-receipts/"+commandID.String())
	httpx.SetVersion(w, receipt.Version)
	return httpx.WriteJSON(w, r, status, receipt)
}
func (h Handler) create(w http.ResponseWriter, r *http.Request) error {
	return h.mutate(w, r, domain.Create)
}
func (h Handler) update(w http.ResponseWriter, r *http.Request) error {
	return h.mutate(w, r, domain.Update)
}
func (h Handler) delete(w http.ResponseWriter, r *http.Request) error {
	return h.mutate(w, r, domain.Delete)
}
func (h Handler) get(w http.ResponseWriter, r *http.Request) error {
	actor, account, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := noQuery(r); err != nil {
		return err
	}
	noteID, err := id.Parse(r.PathValue("noteID"))
	if err != nil {
		return err
	}
	n, err := h.Read.Get(r.Context(), actor, account, noteID)
	if err != nil {
		return err
	}
	httpx.SetVersion(w, n.Version)
	return httpx.WriteJSON(w, r, http.StatusOK, n)
}
func (h Handler) receipt(w http.ResponseWriter, r *http.Request) error {
	actor, account, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := noQuery(r); err != nil {
		return err
	}
	commandID, err := id.Parse(r.PathValue("commandID"))
	if err != nil {
		return err
	}
	receipt, err := h.Read.Receipt(r.Context(), actor, account, commandID)
	if err != nil {
		return err
	}
	httpx.SetVersion(w, receipt.Version)
	return httpx.WriteJSON(w, r, http.StatusOK, receipt)
}
func (h Handler) list(w http.ResponseWriter, r *http.Request) error {
	actor, account, err := h.scope(r)
	if err != nil {
		return err
	}
	values, err := httpQuery(r)
	if err != nil {
		return err
	}
	limit := 20
	var after id.ID
	if v, ok := values["limit"]; ok {
		limit, err = strconv.Atoi(v)
		if err != nil || strconv.Itoa(limit) != v || limit < 1 || limit > 100 {
			return domain.ErrWrite
		}
	}
	if v, ok := values["after"]; ok {
		after, err = id.Parse(v)
		if err != nil {
			return err
		}
	}
	page, err := h.Read.List(r.Context(), actor, account, after, limit)
	if err != nil {
		return err
	}
	var next *id.ID
	if page.Next.Valid() {
		next = &page.Next
	}
	return httpx.WriteJSON(w, r, http.StatusOK, struct {
		AccountID id.ID         `json:"account_id"`
		Notes     []domain.Note `json:"notes"`
		Next      *id.ID        `json:"next_after"`
	}{account, page.Notes, next})
}
func httpQuery(r *http.Request) (map[string]string, error) {
	values, err := url.ParseQuery(r.URL.RawQuery)
	if err != nil {
		return nil, domain.ErrWrite
	}
	result := map[string]string{}
	for k, v := range values {
		if (k != "limit" && k != "after") || len(v) != 1 || v[0] == "" {
			return nil, domain.ErrWrite
		}
		result[k] = v[0]
	}
	return result, nil
}
