// Package postgres owns connection and transaction mechanics, never module SQL.
package postgres

import (
	"context"
	"errors"
	"io"
	"net"
	"time"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

var (
	ErrConfiguration = apperrors.New(apperrors.Invalid, "postgres.configuration", "invalid PostgreSQL connection configuration").WithPublic("Check the PostgreSQL connection configuration.")
	ErrSchema        = apperrors.New(apperrors.Unavailable, "postgres.schema", "missing, changed, or unsupported schema migrations").WithPublic("Run the matching database migrations before starting the API.")
	ErrCommitUnknown = apperrors.New(apperrors.Unavailable, "postgres.commit_unknown", "transaction commit outcome is unknown").WithPublic("The write outcome is uncertain. Read current state before trying again.")
)

type Config struct{ URL string }

func (Config) String() string   { return "postgres.Config{redacted}" }
func (Config) GoString() string { return "postgres.Config{redacted}" }

// Open validates a live connection. Its caller owns Close.
func Open(ctx context.Context, configuration Config) (*pgxpool.Pool, error) {
	if configuration.URL == "" {
		return nil, ErrConfiguration
	}
	options, err := pgxpool.ParseConfig(configuration.URL)
	if err != nil {
		return nil, ErrConfiguration.WithCause(err)
	}
	options.MaxConns = 8
	options.ConnConfig.ConnectTimeout = 5 * time.Second
	options.ConnConfig.RuntimeParams["timezone"] = "UTC"
	options.ConnConfig.RuntimeParams["statement_timeout"] = "5000"
	options.ConnConfig.RuntimeParams["lock_timeout"] = "2000"
	pool, err := pgxpool.NewWithConfig(ctx, options)
	if err != nil {
		return nil, Failure(err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, Failure(err)
	}
	return pool, nil
}

// Failure keeps driver diagnostics private. Modules interpret their constraints.
func Failure(cause error) error {
	if cause == nil {
		return nil
	}
	var owned *apperrors.Error
	if errors.As(cause, &owned) {
		return cause
	}
	kind := apperrors.Internal
	switch {
	case errors.Is(cause, context.Canceled):
		kind = apperrors.Canceled
	case errors.Is(cause, context.DeadlineExceeded):
		kind = apperrors.Timeout
	default:
		var sql *pgconn.PgError
		var network net.Error
		var connection *pgconn.ConnectError
		if errors.As(cause, &sql) {
			switch sql.Code {
			case "57014":
				kind = apperrors.Timeout
			case "40001", "40P01", "55P03", "53300", "53400", "57P01", "57P02", "57P03":
				kind = apperrors.Unavailable
			default:
				if len(sql.Code) >= 2 && sql.Code[:2] == "08" {
					kind = apperrors.Unavailable
				}
			}
		} else if errors.As(cause, &network) {
			kind = apperrors.Unavailable
			if network.Timeout() {
				kind = apperrors.Timeout
			}
		} else if errors.As(cause, &connection) || errors.Is(cause, io.EOF) || errors.Is(cause, io.ErrUnexpectedEOF) {
			kind = apperrors.Unavailable
		}
	}
	return apperrors.New(kind, "postgres.failure", "PostgreSQL operation failed").WithCause(cause)
}

// Transaction does not retry writes. Cancellation before COMMIT rolls back;
// losing a COMMIT response is explicitly uncertain rather than a rollback claim.
func Transaction(ctx context.Context, pool *pgxpool.Pool, work func(pgx.Tx) error) error {
	tx, err := pool.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.ReadCommitted})
	if err != nil {
		return Failure(err)
	}
	defer func() {
		cleanup, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = tx.Rollback(cleanup) // pgx closes a connection if rollback fails.
	}()
	if err := work(tx); err != nil {
		return Failure(err)
	}
	if err := ctx.Err(); err != nil {
		return Failure(err)
	}
	return commit(ctx, tx)
}

func commit(ctx context.Context, tx interface{ Commit(context.Context) error }) error {
	err := tx.Commit(ctx)
	if err == nil {
		return nil
	}
	var sql *pgconn.PgError
	if errors.Is(err, pgx.ErrTxCommitRollback) || errors.Is(err, pgx.ErrTxClosed) {
		return Failure(err)
	}
	if errors.As(err, &sql) && sql.Severity == "ERROR" && len(sql.Code) >= 2 {
		switch sql.Code[:2] {
		case "23", "25", "40":
			return Failure(err) // Explicit constraint/state/transaction rejection.
		}
	}
	return ErrCommitUnknown.WithCause(err)
}
