package v1

import (
	"context"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"net/http"
)

type RecoveryStore interface {
	Contact(context.Context, id.ID) (domain.Contact, bool, error)
}
type RecoveryHandler struct {
	RequestVerification command.RequestEmailVerification
	Verify              command.VerifyEmail
	RequestReset        interface {
		Handle(context.Context, string) error
	}
	Reset      command.ResetPassword
	Change     command.ChangePassword
	Store      RecoveryStore
	Principals principal.Resolver
	Admission  Handler
}

func (h RecoveryHandler) Register(mux *http.ServeMux) {
	mux.Handle("/v1/identity/email/request", httpx.Methods(map[string]httpx.Endpoint{"POST": h.requestVerification}))
	mux.Handle("/v1/identity/email/verify", httpx.Methods(map[string]httpx.Endpoint{"POST": h.verify}))
	mux.Handle("/v1/identity/email", httpx.Methods(map[string]httpx.Endpoint{"GET": h.contact}))
	mux.Handle("/v1/identity/password/request-reset", httpx.Methods(map[string]httpx.Endpoint{"POST": h.requestReset}))
	mux.Handle("/v1/identity/password/reset", httpx.Methods(map[string]httpx.Endpoint{"POST": h.reset}))
	mux.Handle("/v1/identity/password/change", httpx.Methods(map[string]httpx.Endpoint{"POST": h.change}))
}
func empty(w http.ResponseWriter) {
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(204)
}
func (h RecoveryHandler) requestVerification(w http.ResponseWriter, r *http.Request) error {
	if err := h.Admission.admit(r, true); err != nil {
		return err
	}
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	var b struct {
		Email    string `json:"email"`
		Password string `json:"password"`
	}
	if err = httpx.DecodeJSON(w, r, &b); err != nil {
		return err
	}
	if err = h.RequestVerification.Handle(r.Context(), command.RequestEmailVerificationInput{PrincipalID: actor.ID, Email: b.Email, Password: b.Password}); err != nil {
		return err
	}
	empty(w)
	return nil
}
func (h RecoveryHandler) verify(w http.ResponseWriter, r *http.Request) error {
	if err := h.Admission.admit(r, true); err != nil {
		return err
	}
	var b struct {
		Token string `json:"token"`
	}
	if err := httpx.DecodeJSON(w, r, &b); err != nil {
		return err
	}
	if err := h.Verify.Handle(r.Context(), b.Token, provenance(r)); err != nil {
		return err
	}
	empty(w)
	return nil
}
func (h RecoveryHandler) contact(w http.ResponseWriter, r *http.Request) error {
	if err := h.Admission.admit(r, false); err != nil {
		return err
	}
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	c, ok, err := h.Store.Contact(r.Context(), actor.ID)
	if err != nil {
		return err
	}
	var verified string
	if ok {
		verified = c.VerifiedAt.Format("2006-01-02T15:04:05.999999Z")
	}
	return httpx.WriteJSON(w, r, 200, struct {
		Verified   bool   `json:"verified"`
		Email      string `json:"email,omitempty"`
		VerifiedAt string `json:"verified_at,omitempty"`
	}{ok, c.Email, verified})
}
func (h RecoveryHandler) requestReset(w http.ResponseWriter, r *http.Request) error {
	if err := h.Admission.admit(r, true); err != nil {
		return err
	}
	var b struct {
		Email string `json:"email"`
	}
	if err := httpx.DecodeJSON(w, r, &b); err != nil {
		return err
	}
	if _, err := domain.Email(b.Email); err != nil {
		return err
	}
	if err := h.RequestReset.Handle(r.Context(), b.Email); err != nil {
		return err
	}

	return httpx.WriteJSON(w, r, 202, struct {
		Message string `json:"message"`
	}{"If an eligible address exists, recovery instructions may arrive."})
}
func (h RecoveryHandler) reset(w http.ResponseWriter, r *http.Request) error {
	if err := h.Admission.admit(r, true); err != nil {
		return err
	}
	var b struct {
		Token    string `json:"token"`
		Password string `json:"password"`
	}
	if err := httpx.DecodeJSON(w, r, &b); err != nil {
		return err
	}
	if err := h.Reset.Handle(r.Context(), command.ResetPasswordInput{Token: b.Token, Password: b.Password}, provenance(r)); err != nil {
		return err
	}
	empty(w)
	return nil
}
func (h RecoveryHandler) change(w http.ResponseWriter, r *http.Request) error {
	if err := h.Admission.admit(r, true); err != nil {
		return err
	}
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	var b struct {
		Current string `json:"current_password"`
		Next    string `json:"new_password"`
	}
	if err := httpx.DecodeJSON(w, r, &b); err != nil {
		return err
	}
	if err := h.Change.Handle(r.Context(), command.ChangePasswordInput{PrincipalID: actor.ID, CurrentPassword: b.Current, NewPassword: b.Next}, events.Metadata{RequestID: provenance(r).RequestID, CorrelationID: provenance(r).CorrelationID}); err != nil {
		return err
	}
	empty(w)
	return nil
}
