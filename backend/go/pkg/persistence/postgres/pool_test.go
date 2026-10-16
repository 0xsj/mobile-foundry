package postgres

import (
	"context"
	"errors"
	"fmt"
	"io"
	"testing"
	"testing/fstest"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

type commitFailure struct{ err error }

func (c commitFailure) Commit(context.Context) error { return c.err }
func TestFailureAndCommitClassification(t *testing.T) {
	for _, test := range []struct {
		cause error
		kind  apperrors.Kind
	}{
		{context.Canceled, apperrors.Canceled}, {context.DeadlineExceeded, apperrors.Timeout},
		{io.EOF, apperrors.Unavailable}, {&pgconn.PgError{Code: "40001", Message: "private sql"}, apperrors.Unavailable},
		{&pgconn.PgError{Code: "23505", Message: "private value"}, apperrors.Internal},
		{&pgconn.PgError{Code: "57014"}, apperrors.Timeout},
	} {
		err := Failure(test.cause)
		if apperrors.KindOf(err) != test.kind || !errors.Is(err, test.cause) {
			t.Fatal(err)
		}
		if view := apperrors.Public(err); view.Message == "private sql" || view.Message == "private value" {
			t.Fatal(view)
		}
	}
	for _, cause := range []error{io.EOF, context.Canceled, context.DeadlineExceeded} {
		if err := commit(context.Background(), commitFailure{cause}); !errors.Is(err, ErrCommitUnknown) {
			t.Fatal(err)
		}
	}
	if err := commit(context.Background(), commitFailure{pgx.ErrTxCommitRollback}); errors.Is(err, ErrCommitUnknown) {
		t.Fatal(err)
	}
	if err := commit(context.Background(), commitFailure{&pgconn.PgError{Code: "40001", Severity: "ERROR"}}); errors.Is(err, ErrCommitUnknown) {
		t.Fatal("explicit rejected commit became uncertain", err)
	}
	for _, cause := range []error{&pgconn.PgError{Code: "57P01", Severity: "FATAL"}, &pgconn.PgError{Code: "08006", Severity: "ERROR"}} {
		if err := commit(context.Background(), commitFailure{cause}); !errors.Is(err, ErrCommitUnknown) {
			t.Fatal("connection shutdown claimed a known rollback", err)
		}
	}
	configuration := Config{URL: "secret"}
	if fmt.Sprintf("%+v %#v", configuration, configuration) != "postgres.Config{redacted} postgres.Config{redacted}" {
		t.Fatal("URL formatting leaked")
	}
}
func TestMigrationAdmission(t *testing.T) {
	files := fstest.MapFS{"account/0001_initial.sql": {Data: []byte("SELECT 1")}}
	if _, err := Load(files, "account"); err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"../account", "account; DROP SCHEMA audit", "audit"} {
		if _, err := Load(files, name); err == nil {
			t.Fatal("invalid schema admitted", name)
		}
	}
	if _, err := Load(fstest.MapFS{"account/0002_initial.sql": {Data: []byte("SELECT 1")}}, "account"); err == nil {
		t.Fatal("migration gap admitted")
	}
}
