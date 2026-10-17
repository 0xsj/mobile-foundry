package v1

import (
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"net/http"
	"time"
)

func writePushJob(w http.ResponseWriter, r *http.Request, status int, job domain.PushJob) error {
	v := job.Snapshot()
	return httpx.WriteJSON(w, r, status, struct {
		ID        id.ID                `json:"job_id"`
		Status    domain.PushJobStatus `json:"status"`
		Attempts  int                  `json:"attempts"`
		Code      string               `json:"result_code"`
		Retired   bool                 `json:"retired"`
		CreatedAt string               `json:"created_at"`
		UpdatedAt string               `json:"updated_at"`
		ExpiresAt string               `json:"expires_at"`
	}{v.ID, v.Status, v.Attempts, v.Code, v.Retired, v.CreatedAt.Format(time.RFC3339Nano), v.UpdatedAt.Format(time.RFC3339Nano), v.ExpiresAt.Format(time.RFC3339Nano)})
}
func (h InstallationsHandler) jobActor(r *http.Request) (app.InstallationActor, id.ID, error) {
	actor, proof, err := h.actor(r)
	if err != nil {
		return actor, "", err
	}
	installation, err := id.Parse(r.PathValue("installationID"))
	if err != nil {
		return actor, "", err
	}
	value, err := h.Get.Handle(r.Context(), query.GetInstallationInput{Actor: actor, ID: installation, Proof: proof})
	if err != nil {
		return actor, "", err
	}
	v := value.Snapshot()
	if v.PrincipalID != actor.Principal || v.SessionID != actor.Session {
		return actor, "", app.ErrInstallationBinding
	}
	return actor, installation, nil
}
func (h InstallationsHandler) PushJobRoutes(mux *http.ServeMux, queue command.QueuePushPreview, get query.GetPushJob) {
	mux.Handle("/v1/installations/{installationID}/push-jobs", httpx.Methods(map[string]httpx.Endpoint{"POST": func(w http.ResponseWriter, r *http.Request) error {
		actor, installation, err := h.jobActor(r)
		if err != nil {
			return err
		}
		version, err := httpx.RequiredVersion(r)
		if err != nil {
			return err
		}
		var body struct {
			ID string `json:"job_id"`
		}
		if err := httpx.DecodeJSON(w, r, &body); err != nil {
			return err
		}
		identity, err := id.Parse(body.ID)
		if err != nil {
			return err
		}
		job, err := queue.Handle(r.Context(), app.PushLease{InstallationID: installation, PrincipalID: actor.Principal, SessionID: actor.Session, Version: version}, identity)
		if err != nil {
			return err
		}
		return writePushJob(w, r, http.StatusAccepted, job)
	}}))
	mux.Handle("/v1/installations/{installationID}/push-jobs/{jobID}", httpx.Methods(map[string]httpx.Endpoint{"GET": func(w http.ResponseWriter, r *http.Request) error {
		actor, installation, err := h.jobActor(r)
		if err != nil {
			return err
		}
		if r.ContentLength != 0 || len(r.TransferEncoding) > 0 || len(r.Header.Values("If-Match")) > 0 {
			return app.ErrInput
		}
		identity, err := id.Parse(r.PathValue("jobID"))
		if err != nil {
			return err
		}
		job, err := get.Handle(r.Context(), actor, installation, identity)
		if err != nil {
			return err
		}
		return writePushJob(w, r, http.StatusOK, job)
	}}))
}
