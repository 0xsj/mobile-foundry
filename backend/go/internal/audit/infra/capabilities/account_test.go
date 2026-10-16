package capabilities

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"testing"
)

func TestDependencyFailureAndMissingDecisionAreNotDenial(t *testing.T) {
	identity := id.ID("00000000-0000-4000-8000-000000000001")
	for _, sample := range []struct {
		value   any
		failure error
		kind    apperrors.Kind
		allowed bool
	}{
		{value: map[string]bool{"allowed": true}, allowed: true}, {value: map[string]bool{"allowed": false}}, {value: struct{}{}, kind: apperrors.Internal}, {value: map[string]any{"allowed": nil}, kind: apperrors.Internal}, {failure: apperrors.New(apperrors.Unavailable, "store.unavailable", "private"), kind: apperrors.Unavailable},
	} {
		r := capability.NewRegistry()
		_ = r.Register(capability.Provision{Name: AccountAccess, Callers: []string{"audit"}, Handler: func(context.Context, json.RawMessage) (any, error) { return sample.value, sample.failure }})
		allowed, err := (Client{Invoker: r.Client("audit")}).Allowed(context.Background(), identity, identity)
		if sample.kind == "" {
			if err != nil || allowed != sample.allowed {
				t.Fatal("decision mismatch", allowed, err)
			}
		} else if err == nil || apperrors.KindOf(err) != sample.kind {
			t.Fatal("failure became a negative decision", err)
		}
	}
	_, err := (Client{Invoker: capability.NewRegistry().Client("audit")}).Allowed(context.Background(), identity, identity)
	if !errors.Is(err, capability.ErrMissing) {
		t.Fatal("missing provider hidden")
	}
}
