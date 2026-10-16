package v1

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/url"
	"strconv"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type directoryCursor struct {
	Version int   `json:"version"`
	OwnerID id.ID `json:"owner_id"`
	After   id.ID `json:"after"`
}

func (h Handler) bootstrap(w http.ResponseWriter, r *http.Request) error {
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	if r.URL.RawQuery != "" {
		return app.ErrInput
	}
	if err := noReplay(r); err != nil {
		return err
	}
	var body struct {
		Name string `json:"name"`
	}
	if err := httpx.DecodeJSON(w, r, &body); err != nil {
		return err
	}
	result, err := h.Bootstrap.Handle(r.Context(), command.CreateInput{Principal: actor, Name: body.Name, Metadata: metadata(r)})
	if err != nil {
		return err
	}
	status := http.StatusOK
	if result.Created {
		status = http.StatusCreated
	}
	w.Header().Set("Location", "/v1/accounts/"+result.Account.Snapshot().ID.String())
	httpx.SetVersion(w, result.Account.Snapshot().Version)
	return httpx.WriteJSON(w, r, status, project(result.Account))
}

func (h Handler) list(w http.ResponseWriter, r *http.Request) error {
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	parameters, err := url.ParseQuery(r.URL.RawQuery)
	if err != nil {
		return app.ErrInput
	}
	for key, values := range parameters {
		if (key != "limit" && key != "cursor") || len(values) != 1 {
			return app.ErrInput
		}
	}
	limit := 20
	if parameters.Has("limit") {
		value := parameters.Get("limit")
		limit, err = strconv.Atoi(value)
		if err != nil || limit < 1 || limit > 100 || strconv.Itoa(limit) != value {
			return app.ErrInput
		}
	}
	var after id.ID
	if parameters.Has("cursor") {
		value := parameters.Get("cursor")
		if len(value) == 0 || len(value) > 256 {
			return app.ErrInput
		}
		wire, err := base64.RawURLEncoding.Strict().DecodeString(value)
		if err != nil {
			return app.ErrInput
		}
		var position directoryCursor
		if capability.Decode(wire, &position) != nil || position.Version != 1 || position.OwnerID != actor.ID || !position.After.Valid() {
			return app.ErrInput
		}
		after = position.After
	}
	page, err := h.List.Handle(r.Context(), query.ListInput{Principal: actor, After: after, Limit: limit})
	if err != nil {
		return err
	}
	accounts := make([]response, 0, len(page.Accounts))
	for _, account := range page.Accounts {
		accounts = append(accounts, project(account))
	}
	var next *string
	if page.Next != "" {
		wire, err := json.Marshal(directoryCursor{Version: 1, OwnerID: actor.ID, After: page.Next})
		if err != nil {
			return err
		}
		value := base64.RawURLEncoding.EncodeToString(wire)
		next = &value
	}
	return httpx.WriteJSON(w, r, http.StatusOK, struct {
		Accounts []response `json:"accounts"`
		Next     *string    `json:"next_cursor"`
	}{accounts, next})
}
