package errors_test

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"testing"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
)

func TestPublicProjectionAndOwnership(t *testing.T) {
	secret := errors.New("database-password=private")
	fields := map[string]string{"name": "Enter a name."}
	condition := apperrors.New(apperrors.Invalid, "account.invalid_name", "private diagnostic").WithPublic("Check the name.")
	err := condition.WithFields(fields).WithCause(secret)
	fields["name"] = "mutated"
	view := apperrors.Public(fmt.Errorf("operation: %w", err))
	if view.Code != "account.invalid_name" || view.Message != "Check the name." || view.Fields["name"] != "Enter a name." {
		t.Fatalf("bad projection: %+v", view)
	}
	view.Fields["name"] = "changed"
	if apperrors.Public(err).Fields["name"] != "Enter a name." {
		t.Fatal("projection leaked mutable state")
	}
	if !errors.Is(err, condition) || !errors.Is(err, secret) {
		t.Fatal("condition or cause identity lost")
	}
	if errors.Is(err, apperrors.New(apperrors.Invalid, "account.invalid_name", "private diagnostic")) {
		t.Fatal("independent conditions must stay independent")
	}
	encoded, _ := json.Marshal(err)
	if strings.Contains(string(encoded), "private") {
		t.Fatal("generic JSON exposed diagnostics")
	}
	internal := apperrors.Public(apperrors.New(apperrors.Internal, "private.code", "secret").WithPublic("must not escape").WithFields(fields).WithCause(secret))
	if internal.Code != "" || internal.Fields != nil || internal.Message != "An unexpected error occurred." {
		t.Fatalf("internal leak: %+v", internal)
	}
	if apperrors.Public(secret).Kind != apperrors.Internal {
		t.Fatal("unknown defect misclassified")
	}
}

func TestClassification(t *testing.T) {
	kinds := []apperrors.Kind{apperrors.Invalid, apperrors.Unauthenticated, apperrors.Forbidden, apperrors.NotFound, apperrors.Conflict, apperrors.RateLimited, apperrors.Unavailable, apperrors.Timeout, apperrors.Canceled, apperrors.Internal}
	for _, kind := range kinds {
		if apperrors.KindOf(apperrors.New(kind, "test.condition", "diagnostic")) != kind {
			t.Fatalf("lost kind %s", kind)
		}
	}
	if apperrors.KindOf(fmt.Errorf("adapter: %w", context.Canceled)) != apperrors.Canceled || apperrors.KindOf(context.DeadlineExceeded) != apperrors.Timeout {
		t.Fatal("context classification lost")
	}
	var typedNil *apperrors.Error
	if apperrors.Public(typedNil).Kind != apperrors.Internal || apperrors.Public(apperrors.New("unknown", "private", "detail")).Code != "" {
		t.Fatal("unknown or typed-nil error must fail closed")
	}
	known := apperrors.New(apperrors.Invalid, "input.invalid", "private").WithPublic("Check input.")
	if view := apperrors.Public(fmt.Errorf("startup: %w", errors.Join(known, nil))); view.Code != "input.invalid" || view.Message != "Check input." {
		t.Fatal("single cleanup-wrapped failure lost projection", view)
	}
	if apperrors.KindOf(errors.Join(known, errors.New("unexpected defect"))) != apperrors.Internal {
		t.Fatal("joined defect misclassified as expected failure")
	}
	if view := apperrors.Public(fmt.Errorf("operation: %w", errors.Join(known, known))); view.Kind != apperrors.Invalid || view.Code != "" || view.Message != "The request failed." {
		t.Fatal("joined failures attributed to one condition")
	}
}
