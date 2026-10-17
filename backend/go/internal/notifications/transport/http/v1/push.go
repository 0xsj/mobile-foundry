package v1

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"net/http"
)

// PreviewRoutes is registered only when a gateway is explicitly selected.
func (h InstallationsHandler) PreviewRoutes(mux *http.ServeMux, send command.SendPushPreview) {
	mux.Handle("/v1/installations/{installationID}/push-preview", httpx.Methods(map[string]httpx.Endpoint{"POST": func(w http.ResponseWriter, r *http.Request) error {
		a, proof, err := h.actor(r)
		if err != nil {
			return err
		}
		expected, err := httpx.RequiredVersion(r)
		if err != nil {
			return err
		}
		identity, err := id.Parse(r.PathValue("installationID"))
		if err != nil {
			return err
		}
		var body struct {
			PreviewID string `json:"preview_id"`
		}
		if err := httpx.DecodeJSON(w, r, &body); err != nil {
			return err
		}
		preview, err := id.Parse(body.PreviewID)
		if err != nil {
			return err
		}
		i, err := h.Get.Handle(r.Context(), query.GetInstallationInput{Actor: a, ID: identity, Proof: proof})
		if err != nil {
			return err
		}
		v := i.Snapshot()
		if v.PrincipalID != a.Principal || v.SessionID != a.Session {
			return app.ErrInstallationBinding
		}
		if v.Version != expected {
			return app.ErrInstallationConflict
		}
		attempt, err := send.Handle(r.Context(), app.PushLease{InstallationID: identity, PrincipalID: a.Principal, SessionID: a.Session, Version: expected}, preview)
		if err != nil {
			return err
		}
		return httpx.WriteJSON(w, r, http.StatusOK, struct {
			PreviewID   id.ID               `json:"preview_id"`
			Disposition app.PushDisposition `json:"disposition"`
			Retired     bool                `json:"retired"`
		}{preview, attempt.Disposition, attempt.Retired})
	}}))
}
