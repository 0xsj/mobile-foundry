package v1

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"mime"
	"net/http"
	"net/url"
	"strconv"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/app/command"
	"github.com/0xsj/mobile-foundry/backend/internal/files/app/query"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Handler struct {
	Request    command.RequestUpload
	Upload     command.UploadContent
	Complete   command.CompleteUpload
	Delete     command.DeleteFile
	Get        query.GetFile
	List       query.ListFiles
	Download   query.DownloadFile
	Receipt    query.GetReceipt
	Principals principal.Resolver
}

func (h Handler) Register(mux *http.ServeMux) {
	mux.Handle("/v1/accounts/{accountID}/files/uploads", httpx.Methods(map[string]httpx.Endpoint{"POST": h.requestUpload}))
	mux.Handle("/v1/accounts/{accountID}/files", httpx.Methods(map[string]httpx.Endpoint{"GET": h.list}))
	mux.Handle("/v1/accounts/{accountID}/files/{fileID}", httpx.Methods(map[string]httpx.Endpoint{"GET": h.get, "DELETE": h.remove}))
	mux.Handle("/v1/accounts/{accountID}/files/{fileID}/content", httpx.Methods(map[string]httpx.Endpoint{"PUT": h.upload, "GET": h.download}))
	mux.Handle("/v1/accounts/{accountID}/files/{fileID}/complete", httpx.Methods(map[string]httpx.Endpoint{"POST": h.complete}))
	mux.Handle("/v1/receipts/files/{key}", httpx.Methods(map[string]httpx.Endpoint{"GET": h.receipt}))
}

type fileView struct {
	ID          id.ID         `json:"id"`
	AccountID   id.ID         `json:"account_id"`
	OwnerID     id.ID         `json:"owner_id"`
	Name        string        `json:"name"`
	ContentType string        `json:"content_type"`
	ByteSize    int64         `json:"byte_size"`
	SHA256      string        `json:"sha256"`
	Status      domain.Status `json:"status"`
	Version     uint64        `json:"version"`
	CreatedAt   string        `json:"created_at"`
	UpdatedAt   string        `json:"updated_at"`
}

func project(s domain.Snapshot) fileView {
	return fileView{s.ID, s.AccountID, s.OwnerID, s.Spec.Name, s.Spec.ContentType, s.Spec.ByteSize, s.Spec.SHA256, s.Status, s.Version, s.CreatedAt.Format("2006-01-02T15:04:05.999999Z"), s.UpdatedAt.Format("2006-01-02T15:04:05.999999Z")}
}
func receiptView(r app.Receipt) any {
	return struct {
		Key         string   `json:"key"`
		Action      string   `json:"action"`
		PrincipalID id.ID    `json:"principal_id"`
		File        fileView `json:"file"`
	}{r.Key, r.Action, r.Actor, project(r.File)}
}
func metadata(r *http.Request) events.Metadata {
	m := httpx.RequestMetadata(r.Context())
	return events.Metadata{RequestID: m.RequestID, CorrelationID: m.CorrelationID}
}
func (h Handler) scope(r *http.Request) (app.Scope, error) {
	p, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return app.Scope{}, err
	}
	account, err := id.Parse(r.PathValue("accountID"))
	return app.Scope{Actor: p.ID, Account: account}, err
}
func noQuery(r *http.Request) error {
	if r.URL.RawQuery != "" || r.URL.ForceQuery {
		return app.ErrInput
	}
	return nil
}
func noBody(r *http.Request) error {
	if r.ContentLength != 0 || len(r.TransferEncoding) > 0 {
		return httpx.ErrInvalidJSON
	}
	return nil
}
func (h Handler) requestUpload(w http.ResponseWriter, r *http.Request) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := noQuery(r); err != nil {
		return err
	}
	if len(r.Header.Values("If-Match")) > 0 {
		return httpx.ErrPrecondition
	}
	key, err := httpx.IdempotencyKey(r)
	if err != nil {
		return err
	}
	var b struct {
		Name        string `json:"name"`
		ContentType string `json:"content_type"`
		ByteSize    int64  `json:"byte_size"`
		SHA256      string `json:"sha256"`
	}
	if err := httpx.DecodeJSON(w, r, &b); err != nil {
		return err
	}
	result, err := h.Request.Handle(r.Context(), command.RequestUploadInput{Scope: scope, Spec: domain.Spec{Name: b.Name, ContentType: b.ContentType, ByteSize: b.ByteSize, SHA256: b.SHA256}, Key: key, Metadata: metadata(r)})
	if err != nil {
		return err
	}
	w.Header().Set("Location", "/v1/accounts/"+scope.Account.String()+"/files/"+result.File.ID.String())
	httpx.SetVersion(w, result.File.Version)
	return httpx.WriteJSON(w, r, 201, receiptView(result))
}
func (h Handler) get(w http.ResponseWriter, r *http.Request) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := noQuery(r); err != nil {
		return err
	}
	identity, err := id.Parse(r.PathValue("fileID"))
	if err != nil {
		return err
	}
	s, err := h.Get.Handle(r.Context(), query.GetFileInput{Scope: scope, FileID: identity})
	if err != nil {
		return err
	}
	httpx.SetVersion(w, s.Version)
	return httpx.WriteJSON(w, r, 200, project(s))
}
func (h Handler) upload(w http.ResponseWriter, r *http.Request) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := noQuery(r); err != nil {
		return err
	}
	if err := app.Authorize(r.Context(), h.Upload.Access, scope); err != nil {
		return err
	}
	identity, err := id.Parse(r.PathValue("fileID"))
	if err != nil {
		return err
	}
	if len(r.Header.Values("Content-Type")) != 1 {
		return app.ErrContent
	}
	media, params, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil || len(params) != 0 {
		return app.ErrContent
	}
	if len(r.Header.Values("Idempotency-Key")) > 0 || len(r.Header.Values("If-Match")) > 0 {
		return app.ErrInput
	}
	bytes, err := io.ReadAll(http.MaxBytesReader(w, r.Body, domain.MaxBytes))
	if err != nil {
		if canceled := r.Context().Err(); canceled != nil {
			return canceled
		}
		var large *http.MaxBytesError
		if errors.As(err, &large) {
			return httpx.ErrTooLarge.WithCause(err)
		}
		return app.ErrContent.WithCause(err)
	}
	if err := h.Upload.Handle(r.Context(), command.UploadContentInput{Scope: scope, FileID: identity, ContentType: media, Bytes: bytes}); err != nil {
		return err
	}
	return httpx.WriteJSON(w, r, 200, struct {
		Status string `json:"status"`
	}{"stored"})
}
func (h Handler) complete(w http.ResponseWriter, r *http.Request) error { return h.mutate(w, r, false) }
func (h Handler) remove(w http.ResponseWriter, r *http.Request) error   { return h.mutate(w, r, true) }
func (h Handler) mutate(w http.ResponseWriter, r *http.Request, remove bool) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := noQuery(r); err != nil {
		return err
	}
	if err := noBody(r); err != nil {
		return err
	}
	identity, err := id.Parse(r.PathValue("fileID"))
	if err != nil {
		return err
	}
	expected, err := httpx.RequiredVersion(r)
	if err != nil {
		return err
	}
	key, err := httpx.IdempotencyKey(r)
	if err != nil {
		return err
	}
	var result app.Receipt
	if remove {
		result, err = h.Delete.Handle(r.Context(), command.DeleteFileInput{Scope: scope, FileID: identity, Expected: expected, Key: key, Metadata: metadata(r)})
	} else {
		result, err = h.Complete.Handle(r.Context(), command.CompleteUploadInput{Scope: scope, FileID: identity, Expected: expected, Key: key, Metadata: metadata(r)})
	}
	if err != nil {
		return err
	}
	httpx.SetVersion(w, result.File.Version)
	return httpx.WriteJSON(w, r, 200, receiptView(result))
}
func (h Handler) download(w http.ResponseWriter, r *http.Request) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	if err := noQuery(r); err != nil {
		return err
	}
	identity, err := id.Parse(r.PathValue("fileID"))
	if err != nil {
		return err
	}
	content, err := h.Download.Handle(r.Context(), query.DownloadFileInput{Scope: scope, FileID: identity})
	if err != nil {
		return err
	}
	httpx.SetVersion(w, content.File.Version)
	w.Header().Set("Content-Type", content.File.Spec.ContentType)
	w.Header().Set("Content-Disposition", mime.FormatMediaType("attachment", map[string]string{"filename": content.File.Spec.Name}))
	w.Header().Set("Content-Length", strconv.Itoa(len(content.Bytes)))
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(200)
	if r.Method == "HEAD" {
		return nil
	}
	n, err := w.Write(content.Bytes)
	if err == nil && n != len(content.Bytes) {
		return io.ErrShortWrite
	}
	return err
}

type cursor struct {
	Version               int `json:"v"`
	Account, Actor, After id.ID
}

func encodeCursor(scope app.Scope, after id.ID) string {
	wire, _ := json.Marshal(cursor{1, scope.Account, scope.Actor, after})
	return base64.RawURLEncoding.EncodeToString(wire)
}
func decodeCursor(raw string, scope app.Scope) (id.ID, error) {
	if len(raw) > 512 {
		return "", app.ErrInput
	}
	wire, err := base64.RawURLEncoding.DecodeString(raw)
	if err != nil {
		return "", app.ErrInput
	}
	var c cursor
	if json.Unmarshal(wire, &c) != nil || c.Version != 1 || c.Account != scope.Account || c.Actor != scope.Actor || !c.After.Valid() || encodeCursor(scope, c.After) != raw {
		return "", app.ErrInput
	}
	return c.After, nil
}
func (h Handler) list(w http.ResponseWriter, r *http.Request) error {
	scope, err := h.scope(r)
	if err != nil {
		return err
	}
	values, err := url.ParseQuery(r.URL.RawQuery)
	if err != nil {
		return app.ErrInput
	}
	for k, v := range values {
		if (k != "limit" && k != "cursor") || len(v) != 1 || v[0] == "" {
			return app.ErrInput
		}
	}
	limit := 20
	if values.Has("limit") {
		limit, err = strconv.Atoi(values.Get("limit"))
		if err != nil || strconv.Itoa(limit) != values.Get("limit") {
			return app.ErrInput
		}
	}
	var after id.ID
	if values.Has("cursor") {
		after, err = decodeCursor(values.Get("cursor"), scope)
		if err != nil {
			return err
		}
	}
	page, err := h.List.Handle(r.Context(), query.ListFilesInput{Scope: scope, After: after, Limit: limit})
	if err != nil {
		return err
	}
	rows := []fileView{}
	for _, s := range page.Files {
		rows = append(rows, project(s))
	}
	var next *string
	if page.Next != "" {
		value := encodeCursor(scope, page.Next)
		next = &value
	}
	return httpx.WriteJSON(w, r, 200, struct {
		Account id.ID      `json:"account_id"`
		Files   []fileView `json:"files"`
		Next    *string    `json:"next_cursor"`
	}{scope.Account, rows, next})
}
func (h Handler) receipt(w http.ResponseWriter, r *http.Request) error {
	actor, err := httpx.RequirePrincipal(r, h.Principals)
	if err != nil {
		return err
	}
	values, err := url.ParseQuery(r.URL.RawQuery)
	if err != nil || len(values) != 1 || len(values["account_id"]) != 1 {
		return app.ErrInput
	}
	account, err := id.Parse(values.Get("account_id"))
	if err != nil {
		return err
	}
	result, err := h.Receipt.Handle(r.Context(), query.GetReceiptInput{Scope: app.Scope{Actor: actor.ID, Account: account}, Key: r.PathValue("key")})
	if err != nil {
		return err
	}
	httpx.SetVersion(w, result.File.Version)
	return httpx.WriteJSON(w, r, 200, receiptView(result))
}
