// Package httpx owns JSON admission, public failure projection, and request metadata.
package httpx

import (
	"bytes"
	"context"
	"encoding/json"
	stderrors "errors"
	"io"
	"log/slog"
	"maps"
	"mime"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type Endpoint func(http.ResponseWriter, *http.Request) error
type metadataKey struct{}
type Metadata struct{ RequestID, CorrelationID string }

func RequestMetadata(ctx context.Context) Metadata {
	value, _ := ctx.Value(metadataKey{}).(Metadata)
	return value
}

var (
	ErrInvalidJSON          = apperrors.New(apperrors.Invalid, "http.invalid_json", "expected one JSON object").WithPublic("Send one valid JSON object with the documented fields.")
	ErrMediaType            = apperrors.New(apperrors.Invalid, "http.invalid_content_type", "expected application/json").WithPublic("Use Content-Type application/json.")
	ErrTooLarge             = apperrors.New(apperrors.Invalid, "http.body_too_large", "request body exceeds 16384 bytes").WithPublic("The request body is too large.")
	ErrNotFound             = apperrors.New(apperrors.NotFound, "http.route_not_found", "no matching route").WithPublic("The requested resource was not found.")
	ErrMethod               = apperrors.New(apperrors.Invalid, "http.method_not_allowed", "method is not allowed").WithPublic("This method is not supported for this resource.")
	ErrPrecondition         = apperrors.New(apperrors.Invalid, "http.invalid_precondition", "If-Match must be one positive canonical version").WithPublic("Send one strong ETag returned by this API in If-Match.")
	ErrPreconditionRequired = apperrors.New(apperrors.Invalid, "http.precondition_required", "If-Match is required").WithPublic("Read the current resource and send its ETag in If-Match.")
	ErrInvalidKey           = apperrors.New(apperrors.Invalid, "http.invalid_idempotency_key", "invalid or repeated idempotency key").WithPublic("Send one Idempotency-Key of 1 to 128 letters, digits, or ._:- characters.")
	ErrUnsupportedKey       = apperrors.New(apperrors.Invalid, "http.unsupported_idempotency", "idempotency is not implemented for this operation").WithPublic("This operation does not support Idempotency-Key yet.")
)
var tokenPattern = regexp.MustCompile(`^[A-Za-z0-9._:-]{1,128}$`)

// DecodeJSON bounds full body admission and rejects null, arrays, unknown fields,
// and trailing values. Go's decoder retains its usual last-key-wins semantics.
func DecodeJSON(w http.ResponseWriter, r *http.Request, target any) error {
	if len(r.Header.Values("Content-Type")) != 1 {
		return ErrMediaType
	}
	media, _, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil || media != "application/json" {
		return ErrMediaType
	}
	wire, err := io.ReadAll(http.MaxBytesReader(w, r.Body, 16384))
	if err != nil {
		var tooLarge *http.MaxBytesError
		if stderrors.As(err, &tooLarge) {
			return ErrTooLarge
		}
		if r.Context().Err() != nil {
			return r.Context().Err()
		}
		return ErrInvalidJSON.WithCause(err)
	}
	wire = bytes.TrimSpace(wire)
	if len(wire) == 0 || wire[0] != '{' {
		return ErrInvalidJSON
	}
	decoder := json.NewDecoder(bytes.NewReader(wire))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return ErrInvalidJSON.WithCause(err)
	}
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		return ErrInvalidJSON
	}
	return r.Context().Err()
}

func WriteJSON(w http.ResponseWriter, r *http.Request, status int, body any) error {
	if err := r.Context().Err(); err != nil {
		return err
	}
	return writeJSON(w, r, status, body, "application/json; charset=utf-8")
}
func writeJSON(w http.ResponseWriter, r *http.Request, status int, body any, contentType string) error {
	if status < 200 || status > 599 || status == 204 || status == 205 || status == 304 {
		return apperrors.New(apperrors.Internal, "http.invalid_status", "JSON response requires a body-capable HTTP status")
	}
	encoded, err := json.Marshal(body)
	if err != nil {
		return err
	}
	encoded = append(encoded, '\n')
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Content-Length", strconv.Itoa(len(encoded)))
	w.WriteHeader(status)
	if r.Method == http.MethodHead {
		return nil
	}
	n, err := w.Write(encoded)
	if err == nil && n != len(encoded) {
		return io.ErrShortWrite
	}
	return err
}

type Problem struct {
	Type      string            `json:"type"`
	Title     string            `json:"title"`
	Status    int               `json:"status"`
	Detail    string            `json:"detail"`
	Kind      apperrors.Kind    `json:"kind"`
	Code      string            `json:"code,omitempty"`
	Fields    map[string]string `json:"fields,omitempty"`
	RequestID string            `json:"request_id,omitempty"`
}

func Status(err error) int {
	switch {
	case stderrors.Is(err, ErrMediaType):
		return 415
	case stderrors.Is(err, ErrTooLarge):
		return 413
	case stderrors.Is(err, ErrMethod):
		return 405
	case stderrors.Is(err, ErrPreconditionRequired):
		return 428
	}
	switch apperrors.KindOf(err) {
	case apperrors.Invalid:
		return 400
	case apperrors.Unauthenticated:
		return 401
	case apperrors.Forbidden:
		return 403
	case apperrors.NotFound:
		return 404
	case apperrors.Conflict:
		return 409
	case apperrors.RateLimited:
		return 429
	case apperrors.Unavailable:
		return 503
	case apperrors.Timeout:
		return 504
	case apperrors.Canceled:
		return 408
	default:
		return 500
	}
}
func WriteError(w http.ResponseWriter, r *http.Request, err error) error {
	status := Status(err)
	view := apperrors.Public(err)
	if status == 401 {
		w.Header().Set("WWW-Authenticate", `Bearer realm="api"`)
	}
	return writeJSON(w, r, status, Problem{Type: "urn:problem:" + string(view.Kind), Title: http.StatusText(status), Status: status, Detail: view.Message, Kind: view.Kind, Code: view.Code, Fields: view.Fields, RequestID: RequestMetadata(r.Context()).RequestID}, "application/problem+json; charset=utf-8")
}

func RequirePrincipal(r *http.Request, resolver principal.Resolver) (principal.Principal, error) {
	values := r.Header.Values("Authorization")
	if len(values) != 1 {
		return principal.Principal{}, principal.ErrRequired
	}
	scheme, token, found := strings.Cut(values[0], " ")
	if !found || !strings.EqualFold(scheme, "Bearer") || token == "" || strings.ContainsAny(token, " \t\r\n") {
		return principal.Principal{}, principal.ErrRequired
	}
	return resolver.Resolve(r.Context(), token)
}
func SetVersion(w http.ResponseWriter, version uint64) {
	w.Header().Set("ETag", `"`+strconv.FormatUint(version, 10)+`"`)
}
func RequiredVersion(r *http.Request) (uint64, error) {
	values := r.Header.Values("If-Match")
	if len(values) == 0 {
		return 0, ErrPreconditionRequired
	}
	if len(values) != 1 || len(values[0]) < 3 || values[0][0] != '"' || values[0][len(values[0])-1] != '"' {
		return 0, ErrPrecondition
	}
	value := values[0][1 : len(values[0])-1]
	version, err := strconv.ParseUint(value, 10, 53)
	if err != nil || version == 0 || strconv.FormatUint(version, 10) != value {
		return 0, ErrPrecondition
	}
	return version, nil
}
func IdempotencyKey(r *http.Request) (string, error) {
	values := r.Header.Values("Idempotency-Key")
	if len(values) == 0 {
		return "", nil
	}
	if len(values) != 1 || !tokenPattern.MatchString(values[0]) {
		return "", ErrInvalidKey
	}
	return values[0], nil
}

// Methods keeps method refusals in the same problem format as application failures.
func Methods(routes map[string]Endpoint) http.Handler {
	routes = maps.Clone(routes)
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		method := r.Method
		if method == http.MethodHead {
			method = http.MethodGet
		}
		endpoint, found := routes[method]
		if !found {
			allowed := make([]string, 0, len(routes)+1)
			for _, method := range []string{"GET", "HEAD", "POST", "PATCH", "PUT", "DELETE"} {
				if _, ok := routes[method]; ok || (method == "HEAD" && routes["GET"] != nil) {
					allowed = append(allowed, method)
				}
			}
			w.Header().Set("Allow", strings.Join(allowed, ", "))
			report(w, r, ErrMethod)
			return
		}
		if err := endpoint(w, r); err != nil {
			report(w, r, err)
		}
	})
}

type response struct {
	http.ResponseWriter
	status int
}

func (w *response) WriteHeader(status int) {
	if w.status == 0 {
		w.status = status
		w.ResponseWriter.WriteHeader(status)
	}
}
func (w *response) Write(data []byte) (int, error) {
	if w.status == 0 {
		w.WriteHeader(200)
	}
	return w.ResponseWriter.Write(data)
}
func (w *response) Unwrap() http.ResponseWriter { return w.ResponseWriter }

type logKey struct{}

func report(w http.ResponseWriter, r *http.Request, err error) {
	log, _ := r.Context().Value(logKey{}).(*slog.Logger)
	if log != nil {
		view := apperrors.Public(err)
		log.Log(r.Context(), slog.LevelWarn, "request failed", "request_id", RequestMetadata(r.Context()).RequestID, "kind", view.Kind, "code", view.Code)
	}
	if owned, ok := w.(*response); ok && owned.status != 0 {
		return
	}
	if writeErr := WriteError(w, r, err); writeErr != nil && log != nil {
		log.WarnContext(r.Context(), "response write failed", "request_id", RequestMetadata(r.Context()).RequestID)
	}
}

// Boundary owns a fresh request ID, correlation admission, cooperative deadline,
// panic reporting and completion logging. It never logs credentials, URLs or bodies.
func Boundary(next http.Handler, ids id.Generator, log *slog.Logger) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, r *http.Request) {
		w := &response{ResponseWriter: writer}
		ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
		defer cancel()
		ctx = context.WithValue(ctx, logKey{}, log)
		r = r.WithContext(ctx)
		requestID, err := ids.NewID()
		if err != nil || !requestID.Valid() {
			report(w, r, apperrors.New(apperrors.Internal, "http.request_id_failed", "request ID generation failed").WithCause(err))
			return
		}
		metadata := Metadata{RequestID: requestID.String()}
		ctx = context.WithValue(ctx, metadataKey{}, metadata)
		r = r.WithContext(ctx)
		w.Header().Set("X-Request-ID", metadata.RequestID)
		correlations := r.Header.Values("X-Correlation-ID")
		if len(correlations) > 1 || (len(correlations) == 1 && !tokenPattern.MatchString(correlations[0])) {
			report(w, r, apperrors.New(apperrors.Invalid, "http.invalid_correlation_id", "invalid correlation ID").WithPublic("Send one valid X-Correlation-ID of at most 128 characters."))
			return
		}
		if len(correlations) == 1 {
			metadata.CorrelationID = correlations[0]
			w.Header().Set("X-Correlation-ID", metadata.CorrelationID)
			r = r.WithContext(context.WithValue(ctx, metadataKey{}, metadata))
		}
		started := time.Now()
		defer func() {
			if recovered := recover(); recovered != nil {
				log.ErrorContext(r.Context(), "request panic", "request_id", metadata.RequestID)
				report(w, r, apperrors.New(apperrors.Internal, "http.panic", "unexpected request defect"))
			}
			log.InfoContext(r.Context(), "request completed", "request_id", metadata.RequestID, "method", r.Method, "status", w.status, "elapsed_ms", time.Since(started).Milliseconds())
		}()
		next.ServeHTTP(w, r)
	})
}
