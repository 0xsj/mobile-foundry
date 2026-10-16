package v1

import (
	"context"
	"net/http"

	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
)

type ReadyQuery interface{ Handle(context.Context) error }
type Handler struct{ ready ReadyQuery }

func New(ready ReadyQuery) *Handler { return &Handler{ready: ready} }
func (handler *Handler) Routes(mux *http.ServeMux) {
	mux.Handle("/v1/health/live", httpx.Methods(map[string]httpx.Endpoint{"GET": handler.live}))
	mux.Handle("/v1/health/ready", httpx.Methods(map[string]httpx.Endpoint{"GET": handler.checkReady}))
}
func (handler *Handler) live(w http.ResponseWriter, r *http.Request) error {
	return httpx.WriteJSON(w, r, 200, struct {
		Status string `json:"status"`
	}{Status: "ok"})
}
func (handler *Handler) checkReady(w http.ResponseWriter, r *http.Request) error {
	if err := handler.ready.Handle(r.Context()); err != nil {
		return err
	}
	return handler.live(w, r)
}
