package postgres

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/files/app"
	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	producer "github.com/0xsj/mobile-foundry/backend/internal/files/infra/events"
	"github.com/0xsj/mobile-foundry/backend/internal/files/infra/records"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Store struct {
	pool  *pgxpool.Pool
	ids   id.Generator
	owner id.ID
}

func New(pool *pgxpool.Pool, ids id.Generator) (*Store, error) {
	owner, err := ids.NewID()
	if err != nil {
		return nil, err
	}
	return &Store{pool, ids, owner}, nil
}
func restore(row pgx.Row) (domain.File, error) {
	var wire []byte
	var identity, account, status string
	var version int64
	if err := row.Scan(&identity, &account, &status, &version, &wire); err != nil {
		return domain.File{}, err
	}
	f, err := records.Decode(wire)
	if err != nil {
		return f, err
	}
	s := f.Snapshot()
	if s.ID.String() != identity || s.AccountID.String() != account || string(s.Status) != status || int64(s.Version) != version {
		return domain.File{}, domain.ErrState
	}
	return f, nil
}

const columns = "id::text,account_id::text,status,version,snapshot"

func (s *Store) Apply(ctx context.Context, scope app.Scope, c app.Command, candidate id.ID, now time.Time, metadata events.Metadata) (app.Receipt, error) {
	if err := c.Validate(); err != nil {
		return app.Receipt{}, err
	}
	if !scope.Actor.Valid() || !scope.Account.Valid() {
		return app.Receipt{}, app.ErrInput
	}
	hash := records.Fingerprint(c)
	var result app.Receipt
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if c.Key != "" {
			tag, err := tx.Exec(ctx, `INSERT INTO files.receipts(account_id,principal_id,key,fingerprint) VALUES($1,$2,$3,$4) ON CONFLICT DO NOTHING`, scope.Account.String(), scope.Actor.String(), c.Key, hash[:])
			if err != nil {
				return err
			}
			if tag.RowsAffected() == 0 {
				var prior, wire []byte
				if err := tx.QueryRow(ctx, `SELECT fingerprint,result FROM files.receipts WHERE account_id=$1 AND principal_id=$2 AND key=$3`, scope.Account.String(), scope.Actor.String(), c.Key).Scan(&prior, &wire); err != nil {
					return err
				}
				if !bytes.Equal(prior, hash[:]) {
					return app.ErrKey
				}
				result, err = records.DecodeReceipt(wire)
				if err != nil {
					return err
				}
				return admitReceipt(result, scope, c.Key)
			}
		}
		var current *domain.File
		if c.Action != app.Request {
			f, err := restore(tx.QueryRow(ctx, `SELECT `+columns+` FROM files.records WHERE account_id=$1 AND id=$2 FOR UPDATE`, scope.Account.String(), c.FileID.String()))
			if err != nil && !errors.Is(err, pgx.ErrNoRows) {
				return err
			}
			if err == nil {
				current = &f
			}
		}
		next, err := app.Plan(scope, c, current, candidate, now)
		if err != nil {
			return err
		}
		snap := next.Snapshot()
		wire, err := json.Marshal(snap)
		if err != nil {
			return err
		}
		if c.Action == app.Request {
			_, err = tx.Exec(ctx, `INSERT INTO files.records(id,account_id,status,version,snapshot) VALUES($1,$2,$3,$4,$5)`, snap.ID.String(), snap.AccountID.String(), string(snap.Status), int64(snap.Version), wire)
		} else {
			_, err = tx.Exec(ctx, `UPDATE files.records SET status=$3,version=$4,snapshot=$5 WHERE account_id=$1 AND id=$2`, snap.AccountID.String(), snap.ID.String(), string(snap.Status), int64(snap.Version), wire)
		}
		if err != nil {
			var sql *pgconn.PgError
			if errors.As(err, &sql) && sql.Code == "23505" && sql.ConstraintName == "records_pkey" {
				return app.ErrConflict
			}
			return err
		}
		message, err := producer.Message(next.Fact(scope.Actor), metadata, s.ids)
		if err != nil {
			return err
		}
		envelope, err := json.Marshal(message)
		if err != nil {
			return err
		}
		if _, err = tx.Exec(ctx, `INSERT INTO files.outbox(event_id,stream_id,aggregate_version,envelope) VALUES($1,$2,$3,$4)`, message.ID.String(), snap.ID.String(), int64(message.Version), envelope); err != nil {
			return err
		}
		result = app.Receipt{Key: c.Key, Action: c.Action, Actor: scope.Actor, File: snap}
		if c.Key != "" {
			receipt, err := json.Marshal(result)
			if err != nil {
				return err
			}
			_, err = tx.Exec(ctx, `UPDATE files.receipts SET result=$4 WHERE account_id=$1 AND principal_id=$2 AND key=$3`, scope.Account.String(), scope.Actor.String(), c.Key, receipt)
			return err
		}
		return nil
	})
	return result, err
}
func admitReceipt(r app.Receipt, scope app.Scope, key string) error {
	if r.Key != key || r.Actor != scope.Actor || r.File.AccountID != scope.Account {
		return domain.ErrState
	}
	return nil
}
func (s *Store) Receipt(ctx context.Context, scope app.Scope, key string) (app.Receipt, bool, error) {
	var wire []byte
	err := s.pool.QueryRow(ctx, `SELECT result FROM files.receipts WHERE account_id=$1 AND principal_id=$2 AND key=$3`, scope.Account.String(), scope.Actor.String(), key).Scan(&wire)
	if errors.Is(err, pgx.ErrNoRows) {
		return app.Receipt{}, false, nil
	}
	if err != nil {
		return app.Receipt{}, false, storage.Failure(err)
	}
	r, err := records.DecodeReceipt(wire)
	if err == nil {
		err = admitReceipt(r, scope, key)
	}
	return r, err == nil, err
}
func (s *Store) Replay(ctx context.Context, scope app.Scope, c app.Command) (app.Receipt, bool, error) {
	if err := c.Validate(); err != nil {
		return app.Receipt{}, false, err
	}
	if c.Key == "" {
		return app.Receipt{}, false, nil
	}
	var prior, wire []byte
	err := s.pool.QueryRow(ctx, `SELECT fingerprint,result FROM files.receipts WHERE account_id=$1 AND principal_id=$2 AND key=$3`, scope.Account.String(), scope.Actor.String(), c.Key).Scan(&prior, &wire)
	if errors.Is(err, pgx.ErrNoRows) {
		return app.Receipt{}, false, nil
	}
	if err != nil {
		return app.Receipt{}, false, storage.Failure(err)
	}
	hash := records.Fingerprint(c)
	if !bytes.Equal(prior, hash[:]) {
		return app.Receipt{}, false, app.ErrKey
	}
	r, err := records.DecodeReceipt(wire)
	if err == nil {
		err = admitReceipt(r, scope, c.Key)
	}
	return r, err == nil, err
}
func (s *Store) Get(ctx context.Context, account, file id.ID) (domain.File, bool, error) {
	f, err := restore(s.pool.QueryRow(ctx, `SELECT `+columns+` FROM files.records WHERE account_id=$1 AND id=$2`, account.String(), file.String()))
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.File{}, false, nil
	}
	if err != nil {
		return domain.File{}, false, storage.Failure(err)
	}
	return f, true, nil
}
func (s *Store) List(ctx context.Context, account, after id.ID, limit int) ([]domain.File, error) {
	if limit < 1 || limit > 101 {
		return nil, app.ErrInput
	}
	floor := after.String()
	if floor == "" {
		floor = "00000000-0000-0000-0000-000000000000"
	}
	rows, err := s.pool.Query(ctx, `SELECT `+columns+` FROM files.records WHERE account_id=$1 AND id>$2 AND status<>'deleted' ORDER BY id LIMIT $3`, account.String(), floor, limit)
	if err != nil {
		return nil, storage.Failure(err)
	}
	defer rows.Close()
	result := []domain.File{}
	for rows.Next() {
		f, err := restore(rows)
		if err != nil {
			return nil, storage.Failure(err)
		}
		result = append(result, f)
	}
	return result, storage.Failure(rows.Err())
}
