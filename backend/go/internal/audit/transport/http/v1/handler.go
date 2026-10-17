package v1

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"net/http"
	"net/url"
	"strconv"
)

type Reader interface {
	Handle(context.Context, query.Input) (app.Page, error)
}
type Handler struct {
	List     Reader
	Personal interface {
		Handle(context.Context, principal.Principal, uint64, int) (app.Page, error)
	}
	Principals principal.Resolver
}
type cursor struct {
	AccountID   id.ID  `json:"account_id"`
	PrincipalID id.ID  `json:"principal_id,omitempty"`
	After       uint64 `json:"after"`
}
type record struct {
	Sequence      uint64 `json:"sequence"`
	EventID       id.ID  `json:"event_id"`
	AccountID     id.ID  `json:"account_id,omitempty"`
	ActorID       id.ID  `json:"actor_id"`
	Action        string `json:"action"`
	Version       uint64 `json:"aggregate_version"`
	OccurredAt    string `json:"occurred_at"`
	RecordedAt    string `json:"recorded_at"`
	RequestID     string `json:"request_id,omitempty"`
	CorrelationID string `json:"correlation_id,omitempty"`
	PrincipalID   id.ID  `json:"principal_id,omitempty"`
	SessionID     id.ID  `json:"session_id,omitempty"`
	Reason        string `json:"reason,omitempty"`
	FileID        id.ID  `json:"file_id,omitempty"`
}

func (h Handler) Register(mux *http.ServeMux) {
	mux.Handle("/v1/accounts/{accountID}/audit", httpx.Methods(map[string]httpx.Endpoint{"GET": h.list}))
	mux.Handle("/v1/principals/me/audit", httpx.Methods(map[string]httpx.Endpoint{"GET": h.personal}))
}
func (h Handler) list(w http.ResponseWriter, r *http.Request) error {
	return h.read(w, r, false)
}
func (h Handler) personal(w http.ResponseWriter, r *http.Request) error { return h.read(w, r, true) }
func (h Handler) read(w http.ResponseWriter, r *http.Request, personal bool) error {
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	var accountID, personalID id.ID
	if personal {
		personalID = actor.ID
	} else {
		accountID, err = id.Parse(r.PathValue("accountID"))
		if err != nil {
			return err
		}
	}
	parameters, err := url.ParseQuery(r.URL.RawQuery)
	if err != nil {
		return app.ErrInput
	}
	for key, values := range parameters {
		if (key != "cursor" && key != "limit") || len(values) != 1 {
			return app.ErrInput
		}
	}
	limit := 50
	if parameters.Has("limit") {
		limit, err = strconv.Atoi(parameters.Get("limit"))
		if err != nil || limit < 1 || limit > 100 {
			return app.ErrInput
		}
	}
	var after uint64
	if parameters.Has("cursor") {
		value := parameters.Get("cursor")
		if len(value) == 0 || len(value) > 256 {
			return app.ErrInput
		}
		wire, err := base64.RawURLEncoding.Strict().DecodeString(value)
		if err != nil {
			return app.ErrInput
		}
		var position cursor
		if capability.Decode(wire, &position) != nil || position.AccountID != accountID || position.PrincipalID != personalID || position.After == 0 || position.After > 1<<53-1 {
			return app.ErrInput
		}
		after = position.After
	}
	var page app.Page
	if personal {
		page, err = h.Personal.Handle(r.Context(), actor, after, limit)
	} else {
		page, err = h.List.Handle(r.Context(), query.Input{Principal: actor, AccountID: accountID, After: after, Limit: limit})
	}
	if err != nil {
		return err
	}
	rows := make([]record, 0, len(page.Records))
	for _, item := range page.Records {
		e := item.Entry.Evidence()
		rows = append(rows, record{Sequence: item.Sequence, EventID: e.EventID, AccountID: e.AccountID, ActorID: e.ActorID, Action: e.Action, Version: e.Version, OccurredAt: e.OccurredAt.Format("2006-01-02T15:04:05.999999Z"), RecordedAt: item.Entry.RecordedAt().Format("2006-01-02T15:04:05.999999Z"), RequestID: e.RequestID, CorrelationID: e.CorrelationID, PrincipalID: e.PrincipalID, SessionID: e.SessionID, Reason: e.Reason, FileID: e.FileID})
	}
	var next *string
	if page.Next != 0 {
		wire, err := json.Marshal(cursor{AccountID: accountID, PrincipalID: personalID, After: page.Next})
		if err != nil {
			return err
		}
		value := base64.RawURLEncoding.EncodeToString(wire)
		next = &value
	}
	return httpx.WriteJSON(w, r, http.StatusOK, struct {
		Entries []record `json:"entries"`
		Next    *string  `json:"next_cursor"`
	}{rows, next})
}
