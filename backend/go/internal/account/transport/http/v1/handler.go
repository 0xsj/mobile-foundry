package v1

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/account/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"net/http"
)

type Creator interface {
	Handle(context.Context, command.CreateInput) (domain.Account, error)
}
type Renamer interface {
	Handle(context.Context, command.RenameInput) (domain.Account, error)
}
type Reader interface {
	Handle(context.Context, principal.Principal, id.ID) (domain.Account, error)
}
type Handler struct {
	Create    Creator
	Rename    Renamer
	Get       Reader
	Bootstrap interface {
		Handle(context.Context, command.CreateInput) (app.BootstrapResult, error)
	}
	List interface {
		Handle(context.Context, query.ListInput) (app.Page, error)
	}
	Principals principal.Resolver
}
type response struct {
	ID        id.ID  `json:"id"`
	OwnerID   id.ID  `json:"owner_id"`
	Name      string `json:"name"`
	Version   uint64 `json:"version"`
	CreatedAt string `json:"created_at"`
	UpdatedAt string `json:"updated_at"`
}

func project(account domain.Account) response {
	s := account.Snapshot()
	return response{s.ID, s.OwnerID, s.Name, s.Version, s.CreatedAt.Format("2006-01-02T15:04:05.999999Z"), s.UpdatedAt.Format("2006-01-02T15:04:05.999999Z")}
}
func metadata(r *http.Request) events.Metadata {
	value := httpx.RequestMetadata(r.Context())
	return events.Metadata{RequestID: value.RequestID, CorrelationID: value.CorrelationID}
}
func noReplay(r *http.Request) error {
	key, err := httpx.IdempotencyKey(r)
	if err != nil {
		return err
	}
	if key != "" {
		return httpx.ErrUnsupportedKey
	}
	return nil
}
func (h Handler) Register(mux *http.ServeMux) {
	mux.Handle("/v1/accounts", httpx.Methods(map[string]httpx.Endpoint{"POST": h.create, "GET": h.list}))
	mux.Handle("/v1/accounts/bootstrap", httpx.Methods(map[string]httpx.Endpoint{"POST": h.bootstrap}))
	mux.Handle("/v1/accounts/{accountID}", httpx.Methods(map[string]httpx.Endpoint{"GET": h.get, "PATCH": h.rename}))
}
func (h Handler) create(w http.ResponseWriter, r *http.Request) error {
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
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
	account, err := h.Create.Handle(r.Context(), command.CreateInput{Principal: actor, Name: body.Name, Metadata: metadata(r)})
	if err != nil {
		return err
	}
	w.Header().Set("Location", "/v1/accounts/"+account.Snapshot().ID.String())
	httpx.SetVersion(w, account.Snapshot().Version)
	return httpx.WriteJSON(w, r, http.StatusCreated, project(account))
}
func (h Handler) get(w http.ResponseWriter, r *http.Request) error {
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	identity, err := id.Parse(r.PathValue("accountID"))
	if err != nil {
		return err
	}
	account, err := h.Get.Handle(r.Context(), actor, identity)
	if err != nil {
		return err
	}
	httpx.SetVersion(w, account.Snapshot().Version)
	return httpx.WriteJSON(w, r, http.StatusOK, project(account))
}
func (h Handler) rename(w http.ResponseWriter, r *http.Request) error {
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	identity, err := id.Parse(r.PathValue("accountID"))
	if err != nil {
		return err
	}
	if err := noReplay(r); err != nil {
		return err
	}
	version, err := httpx.RequiredVersion(r)
	if err != nil {
		return err
	}
	var body struct {
		Name string `json:"name"`
	}
	if err := httpx.DecodeJSON(w, r, &body); err != nil {
		return err
	}
	account, err := h.Rename.Handle(r.Context(), command.RenameInput{Principal: actor, AccountID: identity, Name: body.Name, ExpectedVersion: version, Metadata: metadata(r)})
	if err != nil {
		return err
	}
	httpx.SetVersion(w, account.Snapshot().Version)
	return httpx.WriteJSON(w, r, http.StatusOK, project(account))
}
