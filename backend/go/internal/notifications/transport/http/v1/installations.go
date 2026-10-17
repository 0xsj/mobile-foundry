package v1

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"net/http"
	"time"
)

type InstallationsHandler struct {
	Register   command.RegisterInstallation
	Get        query.GetInstallation
	Unregister command.UnregisterInstallation
	Principals principal.Resolver
}

func (h InstallationsHandler) Routes(mux *http.ServeMux) {
	mux.Handle("/v1/installations", httpx.Methods(map[string]httpx.Endpoint{"POST": h.create}))
	mux.Handle("/v1/installations/{installationID}", httpx.Methods(map[string]httpx.Endpoint{"GET": h.get, "PUT": h.update, "DELETE": h.remove}))
}
func (h InstallationsHandler) actor(r *http.Request) (app.InstallationActor, string, error) {
	p, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return app.InstallationActor{}, "", err
	}
	if r.URL.RawQuery != "" || r.URL.ForceQuery || len(r.Header.Values("Idempotency-Key")) > 0 || len(r.Header.Values("If-None-Match")) > 0 || len(r.Header.Values("X-Installation-Key")) != 1 {
		return app.InstallationActor{}, "", app.ErrInput
	}
	return app.InstallationActorFrom(p), r.Header.Get("X-Installation-Key"), nil
}

type installationView struct {
	ID             id.ID  `json:"id"`
	RegistrationID id.ID  `json:"registration_id"`
	Version        uint64 `json:"version"`
	Provider       string `json:"provider"`
	Application    string `json:"application"`
	Environment    string `json:"environment"`
	Registered     bool   `json:"registered"`
	CurrentBinding bool   `json:"current_binding"`
	CreatedAt      string `json:"created_at"`
	UpdatedAt      string `json:"updated_at"`
	ExpiresAt      string `json:"expires_at"`
}

func writeInstallation(w http.ResponseWriter, r *http.Request, i domain.Installation, a app.InstallationActor, status int) error {
	v := i.Snapshot()
	httpx.SetVersion(w, v.Version)
	return httpx.WriteJSON(w, r, status, installationView{v.ID, v.RegistrationID, v.Version, v.Spec.Route.Provider, v.Spec.Route.Application, v.Spec.Route.Environment, v.Enabled, v.PrincipalID == a.Principal && v.SessionID == a.Session, v.CreatedAt.Format(time.RFC3339Nano), v.UpdatedAt.Format(time.RFC3339Nano), v.ExpiresAt.Format(time.RFC3339Nano)})
}
func (h InstallationsHandler) create(w http.ResponseWriter, r *http.Request) error {
	return h.register(w, r, true)
}
func (h InstallationsHandler) update(w http.ResponseWriter, r *http.Request) error {
	return h.register(w, r, false)
}

type installationRegistration struct {
	RegistrationID string `json:"registration_id"`
	Provider       string `json:"provider"`
	Application    string `json:"application"`
	Environment    string `json:"environment"`
	Token          string `json:"token"`
}

func (h InstallationsHandler) register(w http.ResponseWriter, r *http.Request, create bool) error {
	a, proof, err := h.actor(r)
	if err != nil {
		return err
	}
	var expected uint64
	if create {
		if len(r.Header.Values("If-Match")) > 0 {
			return app.ErrInput
		}
	} else {
		expected, err = httpx.RequiredVersion(r)
		if err != nil {
			return err
		}
	}
	var body installationRegistration
	var identity id.ID
	if create {
		var input struct {
			ID string `json:"installation_id"`
			installationRegistration
		}
		if err := httpx.DecodeJSON(w, r, &input); err != nil {
			return err
		}
		body = input.installationRegistration
		identity, err = id.Parse(input.ID)
	} else {
		if err := httpx.DecodeJSON(w, r, &body); err != nil {
			return err
		}
		identity, err = id.Parse(r.PathValue("installationID"))
	}
	if err != nil {
		return err
	}
	registration, err := id.Parse(body.RegistrationID)
	if err != nil {
		return err
	}
	i, err := h.Register.Handle(r.Context(), app.RegisterInstallationInput{Actor: a, ID: identity, RegistrationID: registration, Proof: proof, Expected: expected, Spec: domain.InstallationSpec{Route: domain.PushRoute{Provider: body.Provider, Application: body.Application, Environment: body.Environment}, Token: body.Token}})
	if err != nil {
		return err
	}
	status := http.StatusOK
	if create {
		status = http.StatusCreated
		w.Header().Set("Location", "/v1/installations/"+identity.String())
	}
	return writeInstallation(w, r, i, a, status)
}
func (h InstallationsHandler) get(w http.ResponseWriter, r *http.Request) error {
	a, proof, err := h.actor(r)
	if err != nil {
		return err
	}
	if r.ContentLength != 0 || len(r.TransferEncoding) > 0 || len(r.Header.Values("If-Match")) > 0 {
		return app.ErrInput
	}
	identity, err := id.Parse(r.PathValue("installationID"))
	if err != nil {
		return err
	}
	i, err := h.Get.Handle(r.Context(), query.GetInstallationInput{Actor: a, ID: identity, Proof: proof})
	if err != nil {
		return err
	}
	return writeInstallation(w, r, i, a, http.StatusOK)
}
func (h InstallationsHandler) remove(w http.ResponseWriter, r *http.Request) error {
	a, proof, err := h.actor(r)
	if err != nil {
		return err
	}
	if r.ContentLength != 0 || len(r.TransferEncoding) > 0 {
		return app.ErrInput
	}
	identity, err := id.Parse(r.PathValue("installationID"))
	if err != nil {
		return err
	}
	expected, err := httpx.RequiredVersion(r)
	if err != nil {
		return err
	}
	i, err := h.Unregister.Handle(r.Context(), command.UnregisterInstallationInput{Actor: a, ID: identity, Proof: proof, Expected: expected})
	if err != nil {
		return err
	}
	return writeInstallation(w, r, i, a, http.StatusOK)
}
