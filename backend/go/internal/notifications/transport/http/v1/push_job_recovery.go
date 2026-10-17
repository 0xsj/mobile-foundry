package v1

import (
	"bytes"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app/query"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"net/http"
)

type replacementInput struct {
	ID     string
	Accept *bool
}

// Acknowledgement admission cannot rely on the legacy decoder's last-duplicate-wins
// or case-insensitive struct matching. Media/body limits remain owned by httpx.
func (v *replacementInput) UnmarshalJSON(wire []byte) error {
	decoder := json.NewDecoder(bytes.NewReader(wire))
	token, err := decoder.Token()
	if err != nil || token != json.Delim('{') {
		return app.ErrInput
	}
	seen := map[string]bool{}
	for decoder.More() {
		token, err := decoder.Token()
		if err != nil {
			return app.ErrInput
		}
		key, ok := token.(string)
		if !ok || seen[key] {
			return app.ErrInput
		}
		seen[key] = true
		switch key {
		case "job_id":
			if err := decoder.Decode(&v.ID); err != nil {
				return app.ErrInput
			}
		case "accept_possible_duplicate":
			if err := decoder.Decode(&v.Accept); err != nil {
				return app.ErrInput
			}
		default:
			return app.ErrInput
		}
	}
	if _, err := decoder.Token(); err != nil || len(seen) != 2 || v.Accept == nil {
		return app.ErrInput
	}
	return nil
}

func (h InstallationsHandler) PushJobRecoveryRoutes(mux *http.ServeMux, cancel command.CancelPushJob, replace command.ReplacePushJob, get query.GetPushJobReplacement) {
	mux.Handle("/v1/installations/{installationID}/push-jobs/{jobID}/cancel", httpx.Methods(map[string]httpx.Endpoint{"POST": func(w http.ResponseWriter, r *http.Request) error {
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
		job, err := cancel.Handle(r.Context(), actor, installation, identity)
		if err != nil {
			return err
		}
		return writePushJob(w, r, http.StatusOK, job)
	}}))
	mux.Handle("/v1/installations/{installationID}/push-jobs/{jobID}/replacement", httpx.Methods(map[string]httpx.Endpoint{
		"POST": func(w http.ResponseWriter, r *http.Request) error {
			actor, installation, err := h.jobActor(r)
			if err != nil {
				return err
			}
			version, err := httpx.RequiredVersion(r)
			if err != nil {
				return err
			}
			source, err := id.Parse(r.PathValue("jobID"))
			if err != nil {
				return err
			}
			var body replacementInput
			if err := httpx.DecodeJSON(w, r, &body); err != nil {
				return err
			}
			if body.Accept == nil {
				return app.ErrInput
			}
			identity, err := id.Parse(body.ID)
			if err != nil {
				return err
			}
			job, err := replace.Handle(r.Context(), app.PushLease{InstallationID: installation, PrincipalID: actor.Principal, SessionID: actor.Session, Version: version}, source, identity, *body.Accept)
			if err != nil {
				return err
			}
			return writePushJob(w, r, http.StatusAccepted, job)
		},
		"GET": func(w http.ResponseWriter, r *http.Request) error {
			actor, installation, err := h.jobActor(r)
			if err != nil {
				return err
			}
			if r.ContentLength != 0 || len(r.TransferEncoding) > 0 || len(r.Header.Values("If-Match")) > 0 {
				return app.ErrInput
			}
			source, err := id.Parse(r.PathValue("jobID"))
			if err != nil {
				return err
			}
			job, err := get.Handle(r.Context(), actor, installation, source)
			if err != nil {
				return err
			}
			return writePushJob(w, r, http.StatusOK, job)
		},
	}))
}
