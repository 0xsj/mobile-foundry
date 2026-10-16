// Package capability owns versioned synchronous invocation, never business policy.
package capability

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"regexp"
	"slices"
	"strings"
	"sync"
	"time"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
)

const Budget = 2 * time.Second
const MaxBytes = 16384

var namePattern = regexp.MustCompile(`^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+\.v[1-9][0-9]{0,5}$`)
var callerPattern = regexp.MustCompile(`^[a-z][a-z0-9_]{0,63}$`)
var (
	ErrInvalid  = apperrors.New(apperrors.Invalid, "capability.invalid", "invalid capability registration or invocation")
	ErrMissing  = apperrors.New(apperrors.Unavailable, "capability.not_provided", "required capability has no provider").WithPublic("A required service is unavailable.")
	ErrCaller   = apperrors.New(apperrors.Forbidden, "capability.caller_not_allowed", "capability caller is not allowed").WithPublic("This service cannot perform this operation.")
	ErrRequest  = apperrors.New(apperrors.Invalid, "capability.invalid_request", "invalid capability request").WithPublic("Capability input is invalid.")
	ErrResponse = apperrors.New(apperrors.Internal, "capability.invalid_response", "invalid capability response")
)

type Handler func(context.Context, json.RawMessage) (any, error)
type Provision struct {
	Name    string
	Callers []string
	Handler Handler
}
type Invoker interface {
	Invoke(context.Context, string, any, any) error
}
type Registry struct {
	mu        sync.RWMutex
	providers map[string]Provision
	frozen    bool
}

func NewRegistry() *Registry     { return &Registry{providers: make(map[string]Provision)} }
func ValidName(name string) bool { return namePattern.MatchString(name) }
func (r *Registry) Register(provider Provision) error {
	if !ValidName(provider.Name) || provider.Handler == nil || len(provider.Callers) == 0 {
		return ErrInvalid
	}
	for _, caller := range provider.Callers {
		if !callerPattern.MatchString(caller) {
			return ErrInvalid
		}
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.frozen || r.providers[provider.Name].Handler != nil {
		return ErrInvalid
	}
	provider.Callers = slices.Clone(provider.Callers)
	r.providers[provider.Name] = provider
	return nil
}
func (r *Registry) Provides(name string) bool {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.providers[name].Handler != nil
}
func (r *Registry) Freeze()                      { r.mu.Lock(); defer r.mu.Unlock(); r.frozen = true }
func (r *Registry) Client(caller string) Invoker { return local{r, caller} }

type local struct {
	registry *Registry
	caller   string
}

func (l local) Invoke(ctx context.Context, name string, request, response any) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if l.registry == nil || !ValidName(name) || !callerPattern.MatchString(l.caller) {
		return ErrInvalid
	}
	l.registry.mu.RLock()
	provider := l.registry.providers[name]
	l.registry.mu.RUnlock()
	if provider.Handler == nil {
		return ErrMissing
	}
	if !slices.Contains(provider.Callers, l.caller) {
		return ErrCaller
	}
	wire, err := json.Marshal(request)
	if err != nil || !object(wire) {
		return ErrRequest.WithCause(err)
	}
	bounded, cancel := context.WithTimeout(ctx, Budget)
	defer cancel()
	result, err := provider.Handler(bounded, bytes.Clone(wire))
	if bounded.Err() != nil {
		return bounded.Err()
	}
	if err != nil {
		if errors.Is(err, context.Canceled) {
			return context.Canceled
		}
		if errors.Is(err, context.DeadlineExceeded) {
			return context.DeadlineExceeded
		}
		// A local call crosses the same public failure boundary as a remote call.
		view := apperrors.Public(err)
		return apperrors.New(view.Kind, view.Code, "capability provider failed").WithPublic(view.Message).WithFields(view.Fields)
	}
	wire, err = json.Marshal(result)
	if err != nil {
		return ErrResponse.WithCause(err)
	}
	if err := Decode(wire, response); err != nil {
		return ErrResponse.WithCause(err)
	}
	return bounded.Err()
}

// Decode admits one bounded object. Required fields are validated by its owner.
// Like the HTTP decoder, JSON duplicate fields retain Go's last-key-wins behavior.
func Decode(wire []byte, target any) error {
	if !object(wire) {
		return ErrRequest
	}
	decoder := json.NewDecoder(bytes.NewReader(wire))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return ErrRequest.WithCause(err)
	}
	if decoder.Decode(new(any)) != io.EOF {
		return ErrRequest
	}
	return nil
}
func object(wire []byte) bool {
	return len(wire) <= MaxBytes && strings.HasPrefix(strings.TrimSpace(string(wire)), "{")
}
