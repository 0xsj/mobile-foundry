package capability

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"strings"
	"testing"
	"time"
)

func TestLocalAdmissionPolicyAndFailureProjection(t *testing.T) {
	r := NewRegistry()
	calls := 0
	provider := Provision{Name: "account.access.v1", Callers: []string{"audit"}, Handler: func(ctx context.Context, wire json.RawMessage) (any, error) {
		calls++
		var input struct {
			Allowed bool `json:"allowed"`
		}
		if err := Decode(wire, &input); err != nil {
			return nil, err
		}
		return input, nil
	}}
	if err := r.Register(provider); err != nil {
		t.Fatal(err)
	}
	provider.Callers[0] = "attacker"
	var answer struct {
		Allowed *bool `json:"allowed"`
	}
	if !errors.Is(r.Client("attacker").Invoke(context.Background(), provider.Name, map[string]bool{"allowed": true}, &answer), ErrCaller) || calls != 0 {
		t.Fatal("caller policy bypassed")
	}
	if err := r.Client("audit").Invoke(context.Background(), provider.Name, map[string]bool{"allowed": false}, &answer); err != nil || answer.Allowed == nil || *answer.Allowed {
		t.Fatal("successful negative response lost", err)
	}
	if err := r.Client("audit").Invoke(context.Background(), provider.Name, map[string]bool{"extra": true}, &answer); apperrors.KindOf(err) != apperrors.Invalid {
		t.Fatal("unknown request field admitted")
	}
	if err := r.Client("audit").Invoke(context.Background(), "missing.query.v1", struct{}{}, &answer); !errors.Is(err, ErrMissing) {
		t.Fatal("missing provider became an answer")
	}
	private := "database-secret"
	if err := r.Register(Provision{Name: "account.failure.v1", Callers: []string{"audit"}, Handler: func(context.Context, json.RawMessage) (any, error) {
		return nil, apperrors.New(apperrors.Unavailable, "account.store_unavailable", private).WithPublic("Try later.").WithCause(errors.New(private))
	}}); err != nil {
		t.Fatal(err)
	}
	err := r.Client("audit").Invoke(context.Background(), "account.failure.v1", struct{}{}, &answer)
	if apperrors.KindOf(err) != apperrors.Unavailable || apperrors.Public(err).Message != "Try later." || strings.Contains(err.Error(), private) || errors.Unwrap(err) != nil {
		t.Fatal("private provider failure leaked or lost classification", err)
	}
	_ = r.Register(Provision{Name: "account.cancel.v1", Callers: []string{"audit"}, Handler: func(context.Context, json.RawMessage) (any, error) {
		return nil, fmt.Errorf("%s: %w", private, context.Canceled)
	}})
	err = r.Client("audit").Invoke(context.Background(), "account.cancel.v1", struct{}{}, &answer)
	if !errors.Is(err, context.Canceled) || strings.Contains(err.Error(), private) {
		t.Fatal("cancellation identity or privacy was lost", err)
	}
	r.Freeze()
	if r.Register(provider) == nil {
		t.Fatal("frozen registration accepted")
	}
}
func TestCancellationDeadlineAndInvalidResponses(t *testing.T) {
	r := NewRegistry()
	_ = r.Register(Provision{Name: "account.wait.v1", Callers: []string{"audit"}, Handler: func(ctx context.Context, _ json.RawMessage) (any, error) { <-ctx.Done(); return struct{}{}, nil }})
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Millisecond)
	defer cancel()
	var answer struct{}
	if err := r.Client("audit").Invoke(ctx, "account.wait.v1", struct{}{}, &answer); !errors.Is(err, context.DeadlineExceeded) {
		t.Fatal("late success admitted", err)
	}
	for _, value := range []any{nil, []string{}, map[string]any{"extra": true}, make(chan int), map[string]string{"large": strings.Repeat("x", MaxBytes)}} {
		registry := NewRegistry()
		_ = registry.Register(Provision{Name: "account.query.v1", Callers: []string{"audit"}, Handler: func(context.Context, json.RawMessage) (any, error) { return value, nil }})
		if !errors.Is(registry.Client("audit").Invoke(context.Background(), "account.query.v1", struct{}{}, &answer), ErrResponse) {
			t.Fatalf("invalid response admitted: %T", value)
		}
	}
	for _, wire := range []string{"null", "[]", "{} {}", `{"extra":1}`} {
		if Decode([]byte(wire), &answer) == nil {
			t.Fatal("invalid shape admitted", wire)
		}
	}
}
