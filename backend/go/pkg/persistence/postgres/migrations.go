package postgres

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io/fs"
	"regexp"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Migration struct{ Name, SQL string }
type Schema struct {
	Name       string
	Migrations []Migration
}

var identifier = regexp.MustCompile(`^[a-z][a-z0-9_]*$`)

func Load(files fs.FS, name string) (Schema, error) {
	if !identifier.MatchString(name) {
		return Schema{}, ErrConfiguration
	}
	paths, err := fs.Glob(files, name+"/*.sql")
	if err != nil || len(paths) == 0 {
		return Schema{}, ErrSchema
	}
	schema := Schema{Name: name}
	for i, path := range paths {
		filename := strings.TrimPrefix(path, name+"/")
		if !strings.HasPrefix(filename, fmt.Sprintf("%04d_", i+1)) {
			return Schema{}, ErrSchema
		}
		data, err := fs.ReadFile(files, path)
		if err != nil {
			return Schema{}, ErrSchema.WithCause(err)
		}
		schema.Migrations = append(schema.Migrations, Migration{filename, string(data)})
	}
	return schema, nil
}
func (s Schema) valid() bool { return identifier.MatchString(s.Name) && len(s.Migrations) > 0 }
func checksum(m Migration) string {
	sum := sha256.Sum256([]byte(m.SQL))
	return hex.EncodeToString(sum[:])
}

type queryer interface {
	Query(context.Context, string, ...any) (pgx.Rows, error)
}

func (s Schema) applied(ctx context.Context, db queryer) (int, error) {
	rows, err := db.Query(ctx, "SELECT name, checksum FROM "+pgx.Identifier{s.Name, "schema_migrations"}.Sanitize()+" ORDER BY name")
	if err != nil {
		var sql *pgconn.PgError
		if errors.As(err, &sql) && (sql.Code == "42P01" || sql.Code == "3F000") {
			return 0, ErrSchema.WithCause(err)
		}
		return 0, Failure(err)
	}
	defer rows.Close()
	n := 0
	for rows.Next() {
		var name, digest string
		if err := rows.Scan(&name, &digest); err != nil {
			return 0, Failure(err)
		}
		if n >= len(s.Migrations) || name != s.Migrations[n].Name || digest != checksum(s.Migrations[n]) {
			return 0, ErrSchema
		}
		n++
	}
	return n, Failure(rows.Err())
}

// Check is read-only. The server never applies migrations implicitly.
func (s Schema) Check(ctx context.Context, pool *pgxpool.Pool) error {
	if !s.valid() {
		return ErrConfiguration
	}
	n, err := s.applied(ctx, pool)
	if err != nil {
		return err
	}
	if n != len(s.Migrations) {
		return ErrSchema
	}
	return nil
}

// Apply serializes migrators per schema and commits each file with its checksum.
// Completed earlier files remain applied if a later file fails.
func (s Schema) Apply(ctx context.Context, pool *pgxpool.Pool) error {
	if !s.valid() {
		return ErrConfiguration
	}
	for index := range s.Migrations {
		if err := Transaction(ctx, pool, func(tx pgx.Tx) error {
			var lock any
			if err := tx.QueryRow(ctx, "SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", "mobile-foundry/migrations/"+s.Name).Scan(&lock); err != nil {
				return err
			}
			quoted := pgx.Identifier{s.Name}.Sanitize()
			var exists bool
			if err := tx.QueryRow(ctx, "SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname=$1)", s.Name).Scan(&exists); err != nil {
				return err
			}
			if !exists {
				if _, err := tx.Exec(ctx, "CREATE SCHEMA "+quoted); err != nil {
					return err
				}
			}
			if _, err := tx.Exec(ctx, "CREATE TABLE IF NOT EXISTS "+quoted+".schema_migrations (name text PRIMARY KEY, checksum text NOT NULL, applied_at timestamptz NOT NULL DEFAULT clock_timestamp())"); err != nil {
				return err
			}
			count, err := s.applied(ctx, tx)
			if err != nil || count > index {
				return err
			}
			if count != index {
				return ErrSchema
			}
			migration := s.Migrations[index]
			if _, err := tx.Exec(ctx, migration.SQL); err != nil {
				return err
			}
			_, err = tx.Exec(ctx, "INSERT INTO "+quoted+".schema_migrations(name, checksum) VALUES ($1,$2)", migration.Name, checksum(migration))
			return err
		}); err != nil {
			return err
		}
	}
	return s.Check(ctx, pool)
}
