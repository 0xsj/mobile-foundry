package query

import (
	"context"
	"errors"
	"testing"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
)

func TestReadinessFailureAndCancellation(t *testing.T) {
	cause := errors.New("private dependency diagnostic")
	handler, err := NewCheckReady(ProbeFunc(func(context.Context) error { return cause }))
	if err != nil {
		t.Fatal(err)
	}
	failure := handler.Handle(context.Background())
	if !errors.Is(failure, cause) || apperrors.Public(failure).Kind != apperrors.Unavailable || apperrors.Public(failure).Code != "health.not_ready" {
		t.Fatal("readiness failure lost its meaning or cause")
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if !errors.Is(handler.Handle(ctx), context.Canceled) {
		t.Fatal("readiness cancellation misclassified")
	}
	if _, err := NewCheckReady(nil); err == nil {
		t.Fatal("missing probe accepted")
	}
}
