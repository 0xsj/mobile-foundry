package v1

import (
	"context"
	"net"
	"net/http"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Registrar interface {
	Handle(context.Context, string, string, string, events.Metadata) (domain.Principal, error)
}
type Signer interface {
	Handle(context.Context, string, string, events.Metadata) (app.Grant, error)
}
type Renewer interface {
	Handle(context.Context, string, events.Metadata) (app.Grant, error)
}
type Revoker interface {
	Handle(context.Context, string, events.Metadata) error
}
type Reader interface {
	Handle(context.Context, id.ID) (domain.Principal, error)
}
type AttemptGate interface {
	Allow(context.Context, string) error
}
type Handler struct {
	RegisterPrincipal Registrar
	SignIn            Signer
	Renew             Renewer
	SignOut           Revoker
	Me                Reader
	Principals        principal.Resolver
	Gate              AttemptGate
}
type profile struct {
	ID          id.ID  `json:"id"`
	Login       string `json:"login"`
	DisplayName string `json:"display_name"`
}

func project(p domain.Principal) profile {
	v := p.Snapshot()
	return profile{v.ID, v.Login, v.DisplayName}
}
func provenance(r *http.Request) events.Metadata {
	m := httpx.RequestMetadata(r.Context())
	return events.Metadata{RequestID: m.RequestID, CorrelationID: m.CorrelationID}
}
func (h Handler) Register(mux *http.ServeMux) {
	mux.Handle("/v1/identity/register", httpx.Methods(map[string]httpx.Endpoint{"POST": h.register}))
	mux.Handle("/v1/identity/sign-in", httpx.Methods(map[string]httpx.Endpoint{"POST": h.signIn}))
	mux.Handle("/v1/identity/renew", httpx.Methods(map[string]httpx.Endpoint{"POST": h.renew}))
	mux.Handle("/v1/identity/sign-out", httpx.Methods(map[string]httpx.Endpoint{"POST": h.signOut}))
	mux.Handle("/v1/identity/me", httpx.Methods(map[string]httpx.Endpoint{"GET": h.me}))
}
func (h Handler) admit(r *http.Request, attempt bool) error {
	if r.URL.RawQuery != "" {
		return domain.ErrInput
	}
	key, err := httpx.IdempotencyKey(r)
	if err != nil {
		return err
	}
	if key != "" {
		return httpx.ErrUnsupportedKey
	}
	if attempt {
		host, _, err := net.SplitHostPort(r.RemoteAddr)
		if err != nil {
			return domain.ErrInput
		}
		return h.Gate.Allow(r.Context(), host+"/"+r.URL.Path)
	}
	return nil
}
func (h Handler) register(w http.ResponseWriter, r *http.Request) error {
	if err := h.admit(r, true); err != nil {
		return err
	}
	var body struct {
		Login       string `json:"login"`
		DisplayName string `json:"display_name"`
		Password    string `json:"password"`
	}
	if err := httpx.DecodeJSON(w, r, &body); err != nil {
		return err
	}
	p, err := h.RegisterPrincipal.Handle(r.Context(), body.Login, body.DisplayName, body.Password, provenance(r))
	if err != nil {
		return err
	}
	return httpx.WriteJSON(w, r, 201, project(p))
}
func writeGrant(w http.ResponseWriter, r *http.Request, g app.Grant) error {
	v := g.Session.Snapshot()
	return httpx.WriteJSON(w, r, 200, struct {
		AuthorityID      string  `json:"authority_id"`
		Principal        profile `json:"principal"`
		SessionID        id.ID   `json:"session_id"`
		Version          uint64  `json:"session_version"`
		AccessToken      string  `json:"access_token"`
		RefreshToken     string  `json:"refresh_token"`
		AccessExpiresAt  string  `json:"access_expires_at"`
		RefreshExpiresAt string  `json:"refresh_expires_at"`
	}{"go.identity.v1", project(g.Principal), v.ID, v.Generation, g.AccessToken(), g.RefreshToken(), v.AccessExpiresAt.Format("2006-01-02T15:04:05.999999Z"), v.ExpiresAt.Format("2006-01-02T15:04:05.999999Z")})
}
func (h Handler) signIn(w http.ResponseWriter, r *http.Request) error {
	if err := h.admit(r, true); err != nil {
		return err
	}
	var body struct {
		Login    string `json:"login"`
		Password string `json:"password"`
	}
	if err := httpx.DecodeJSON(w, r, &body); err != nil {
		return err
	}
	g, err := h.SignIn.Handle(r.Context(), body.Login, body.Password, provenance(r))
	if err != nil {
		return err
	}
	return writeGrant(w, r, g)
}
func (h Handler) renew(w http.ResponseWriter, r *http.Request) error {
	if err := h.admit(r, true); err != nil {
		return err
	}
	var body struct {
		RefreshToken string `json:"refresh_token"`
	}
	if err := httpx.DecodeJSON(w, r, &body); err != nil {
		return err
	}
	g, err := h.Renew.Handle(r.Context(), body.RefreshToken, provenance(r))
	if err != nil {
		return err
	}
	return writeGrant(w, r, g)
}
func (h Handler) signOut(w http.ResponseWriter, r *http.Request) error {
	if err := h.admit(r, true); err != nil {
		return err
	}
	var body struct {
		RefreshToken string `json:"refresh_token"`
	}
	if err := httpx.DecodeJSON(w, r, &body); err != nil {
		return err
	}
	if err := h.SignOut.Handle(r.Context(), body.RefreshToken, provenance(r)); err != nil {
		return err
	}
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(204)
	return nil
}
func (h Handler) me(w http.ResponseWriter, r *http.Request) error {
	if err := h.admit(r, false); err != nil {
		return err
	}
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	p, err := h.Me.Handle(r.Context(), actor.ID)
	if err != nil {
		return err
	}
	return httpx.WriteJSON(w, r, 200, project(p))
}
