package v1

import (
	"encoding/base64"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"net/http"
	"net/url"
	"strconv"
	"time"
)

type Handler struct {
	List          query.ListInbox
	Read          command.MarkRead
	Preferences   query.GetPreferences
	SetPreference command.SetMediaPreference
	Principals    principal.Resolver
}

func (h Handler) Register(mux *http.ServeMux) {
	mux.Handle("/v1/accounts/{accountID}/notifications", httpx.Methods(map[string]httpx.Endpoint{"GET": h.list}))
	mux.Handle("/v1/accounts/{accountID}/notifications/{itemID}/read", httpx.Methods(map[string]httpx.Endpoint{"POST": h.read}))
	mux.Handle("/v1/accounts/{accountID}/notification-preferences", httpx.Methods(map[string]httpx.Endpoint{"GET": h.preferences, "PUT": h.setPreference}))
}
func (h Handler) scope(r *http.Request) (app.Scope, error) {
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return app.Scope{}, err
	}
	account, err := id.Parse(r.PathValue("accountID"))
	return app.Scope{Actor: actor.ID, Account: account}, err
}
func plain(r *http.Request, body bool) error {
	if r.URL.RawQuery != "" || r.URL.ForceQuery || len(r.Header.Values("Idempotency-Key")) > 0 || len(r.Header.Values("If-Match")) > 0 {
		return app.ErrInput
	}
	if !body && (r.ContentLength != 0 || len(r.TransferEncoding) > 0) {
		return app.ErrInput
	}
	return nil
}

type itemView struct {
	ID         id.ID   `json:"id"`
	FileID     id.ID   `json:"file_id"`
	Type       string  `json:"type"`
	Category   string  `json:"category"`
	OccurredAt string  `json:"occurred_at"`
	CreatedAt  string  `json:"created_at"`
	ReadAt     *string `json:"read_at"`
}

func project(item domain.Item) itemView {
	v := item.Snapshot()
	var read *string
	if !v.ReadAt.IsZero() {
		value := v.ReadAt.Format(time.RFC3339Nano)
		read = &value
	}
	return itemView{v.ID, v.FileID, v.Type, v.Category, v.OccurredAt.Format(time.RFC3339Nano), v.CreatedAt.Format(time.RFC3339Nano), read}
}

type cursor struct {
	Version int    `json:"v"`
	Actor   id.ID  `json:"principal_id"`
	Account id.ID  `json:"account_id"`
	Before  uint64 `json:"before"`
	Unread  bool   `json:"unread"`
}

func encode(scope app.Scope, before uint64, unread bool) string {
	wire, _ := json.Marshal(cursor{1, scope.Actor, scope.Account, before, unread})
	return base64.RawURLEncoding.EncodeToString(wire)
}
func (h Handler) list(w http.ResponseWriter, r *http.Request) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	if r.ContentLength != 0 || len(r.TransferEncoding) > 0 {
		return app.ErrInput
	}
	parameters, err := url.ParseQuery(r.URL.RawQuery)
	if err != nil || r.URL.ForceQuery {
		return app.ErrInput
	}
	for k, v := range parameters {
		if (k != "limit" && k != "cursor" && k != "unread") || len(v) != 1 || v[0] == "" {
			return app.ErrInput
		}
	}
	input := app.ListInput{Scope: scope, Limit: 20}
	if parameters.Has("limit") {
		input.Limit, err = strconv.Atoi(parameters.Get("limit"))
		if err != nil || strconv.Itoa(input.Limit) != parameters.Get("limit") {
			return app.ErrInput
		}
	}
	if parameters.Has("unread") {
		if parameters.Get("unread") != "true" && parameters.Get("unread") != "false" {
			return app.ErrInput
		}
		input.UnreadOnly = parameters.Get("unread") == "true"
	}
	if parameters.Has("cursor") {
		raw := parameters.Get("cursor")
		if len(raw) > 512 {
			return app.ErrInput
		}
		wire, err := base64.RawURLEncoding.Strict().DecodeString(raw)
		if err != nil {
			return app.ErrInput
		}
		var c cursor
		if json.Unmarshal(wire, &c) != nil || c.Version != 1 || c.Actor != scope.Actor || c.Account != scope.Account || c.Unread != input.UnreadOnly || c.Before == 0 || c.Before > 1<<53-1 || encode(scope, c.Before, c.Unread) != raw {
			return app.ErrInput
		}
		input.Before = c.Before
	}
	page, err := h.List.Handle(r.Context(), input)
	if err != nil {
		return err
	}
	items := []itemView{}
	for _, item := range page.Items {
		items = append(items, project(item))
	}
	var next *string
	if page.Next != 0 {
		value := encode(scope, page.Next, input.UnreadOnly)
		next = &value
	}
	return httpx.WriteJSON(w, r, 200, struct {
		AccountID   id.ID      `json:"account_id"`
		Items       []itemView `json:"items"`
		UnreadCount uint64     `json:"unread_count"`
		Next        *string    `json:"next_cursor"`
	}{scope.Account, items, page.UnreadCount, next})
}
func (h Handler) read(w http.ResponseWriter, r *http.Request) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := plain(r, false); err != nil {
		return err
	}
	identity, err := id.Parse(r.PathValue("itemID"))
	if err != nil {
		return err
	}
	item, err := h.Read.Handle(r.Context(), command.MarkReadInput{Scope: scope, ItemID: identity})
	if err != nil {
		return err
	}
	return httpx.WriteJSON(w, r, 200, project(item))
}
func preferenceView(w http.ResponseWriter, r *http.Request, scope app.Scope, enabled bool) error {
	return httpx.WriteJSON(w, r, 200, struct {
		AccountID  id.ID `json:"account_id"`
		MediaInApp bool  `json:"media_in_app"`
	}{scope.Account, enabled})
}
func (h Handler) preferences(w http.ResponseWriter, r *http.Request) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := plain(r, false); err != nil {
		return err
	}
	enabled, err := h.Preferences.Handle(r.Context(), scope)
	if err != nil {
		return err
	}
	return preferenceView(w, r, scope, enabled)
}
func (h Handler) setPreference(w http.ResponseWriter, r *http.Request) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := plain(r, true); err != nil {
		return err
	}
	var body struct {
		MediaInApp *bool `json:"media_in_app"`
	}
	if err := httpx.DecodeJSON(w, r, &body); err != nil {
		return err
	}
	if body.MediaInApp == nil {
		return app.ErrInput
	}
	if err := h.SetPreference.Handle(r.Context(), command.SetMediaPreferenceInput{Scope: scope, Enabled: *body.MediaInApp}); err != nil {
		return err
	}
	return preferenceView(w, r, scope, *body.MediaInApp)
}
