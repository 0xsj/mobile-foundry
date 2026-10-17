package postgres

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/records"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"time"
)

type Store struct{ pool *pgxpool.Pool }

func New(pool *pgxpool.Pool) *Store { return &Store{pool: pool} }

// Every operation that can change a scope's decision or view uses this same
// guard. No capability/provider call runs inside this owned transaction.
func lockScope(ctx context.Context, tx pgx.Tx, scope app.Scope) (bool, uint64, error) {
	if _, err := tx.Exec(ctx, `INSERT INTO notifications.scope_state(account_id,principal_id) VALUES($1,$2) ON CONFLICT DO NOTHING`, scope.Account.String(), scope.Actor.String()); err != nil {
		return false, 0, err
	}
	var enabled bool
	var next int64
	err := tx.QueryRow(ctx, `SELECT media_enabled,next_sequence FROM notifications.scope_state WHERE account_id=$1 AND principal_id=$2 FOR UPDATE`, scope.Account.String(), scope.Actor.String()).Scan(&enabled, &next)
	return enabled, uint64(next), err
}
func (s *Store) Deliver(ctx context.Context, d app.Delivery, at time.Time) error {
	if err := d.Validate(); err != nil {
		return err
	}
	if at.IsZero() {
		return app.ErrInput
	}
	hash := records.Fingerprint(d)
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `INSERT INTO notifications.inbox(consumer,event_id,fingerprint) VALUES('notifications.files',$1,$2) ON CONFLICT DO NOTHING`, d.EventID.String(), hash)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			var prior string
			if err := tx.QueryRow(ctx, `SELECT fingerprint FROM notifications.inbox WHERE consumer='notifications.files' AND event_id=$1`, d.EventID.String()).Scan(&prior); err != nil {
				return err
			}
			if prior != hash {
				return app.ErrReplay
			}
			return nil
		}
		scope := app.Scope{Actor: d.PrincipalID, Account: d.AccountID}
		enabled, next, err := lockScope(ctx, tx, scope)
		if err != nil {
			return err
		}
		if !enabled {
			return nil
		}
		if next >= 1<<53-1 {
			return app.ErrCapacity
		}
		item, err := d.Item(next, at)
		if err != nil {
			return err
		}
		v := item.Snapshot()
		if _, err := tx.Exec(ctx, `INSERT INTO notifications.items(id,account_id,principal_id,file_id,sequence,type,category,occurred_at,created_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9)`, v.ID.String(), v.AccountID.String(), v.PrincipalID.String(), v.FileID.String(), int64(v.Sequence), v.Type, v.Category, v.OccurredAt, v.CreatedAt); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `UPDATE notifications.scope_state SET next_sequence=next_sequence+1 WHERE account_id=$1 AND principal_id=$2`, scope.Account.String(), scope.Actor.String())
		return err
	})
}

const columns = "id::text,account_id::text,principal_id::text,file_id::text,sequence,type,category,occurred_at,created_at,read_at"

func restore(row pgx.Row) (domain.Item, error) {
	var identity, account, principal, file string
	var sequence int64
	var read *time.Time
	var v domain.Snapshot
	if err := row.Scan(&identity, &account, &principal, &file, &sequence, &v.Type, &v.Category, &v.OccurredAt, &v.CreatedAt, &read); err != nil {
		return domain.Item{}, err
	}
	var err error
	if v.ID, err = id.Parse(identity); err != nil {
		return domain.Item{}, domain.ErrState
	}
	if v.AccountID, err = id.Parse(account); err != nil {
		return domain.Item{}, domain.ErrState
	}
	if v.PrincipalID, err = id.Parse(principal); err != nil {
		return domain.Item{}, domain.ErrState
	}
	if v.FileID, err = id.Parse(file); err != nil {
		return domain.Item{}, domain.ErrState
	}
	v.Sequence = uint64(sequence)
	if read != nil {
		v.ReadAt = *read
	}
	return domain.Restore(v)
}
func (s *Store) List(ctx context.Context, input app.ListInput) (app.Page, error) {
	if err := input.Validate(); err != nil {
		return app.Page{}, err
	}
	page := app.Page{Items: []domain.Item{}}
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if _, _, err := lockScope(ctx, tx, input.Scope); err != nil {
			return err
		}
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM notifications.items WHERE account_id=$1 AND principal_id=$2 AND read_at IS NULL`, input.Scope.Account.String(), input.Scope.Actor.String()).Scan(&page.UnreadCount); err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `SELECT `+columns+` FROM notifications.items WHERE account_id=$1 AND principal_id=$2 AND ($3::bigint=0 OR sequence<$3) AND (NOT $4::boolean OR read_at IS NULL) ORDER BY sequence DESC LIMIT $5`, input.Scope.Account.String(), input.Scope.Actor.String(), int64(input.Before), input.UnreadOnly, input.Limit+1)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			item, err := restore(rows)
			if err != nil {
				return err
			}
			if len(page.Items) == input.Limit {
				page.Next = page.Items[input.Limit-1].Snapshot().Sequence
				break
			}
			page.Items = append(page.Items, item)
		}
		return rows.Err()
	})
	return page, err
}
func (s *Store) MarkRead(ctx context.Context, scope app.Scope, identity id.ID, at time.Time) (domain.Item, error) {
	if !scope.Valid() || !identity.Valid() || at.IsZero() {
		return domain.Item{}, app.ErrInput
	}
	var item domain.Item
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if _, _, err := lockScope(ctx, tx, scope); err != nil {
			return err
		}
		var err error
		item, err = restore(tx.QueryRow(ctx, `UPDATE notifications.items SET read_at=COALESCE(read_at,$4) WHERE account_id=$1 AND principal_id=$2 AND id=$3 RETURNING `+columns, scope.Account.String(), scope.Actor.String(), identity.String(), at.UTC().Truncate(time.Microsecond)))
		if errors.Is(err, pgx.ErrNoRows) {
			return app.ErrMissing
		}
		return err
	})
	return item, err
}
func (s *Store) MediaEnabled(ctx context.Context, scope app.Scope) (bool, error) {
	if !scope.Valid() {
		return false, app.ErrInput
	}
	var enabled bool
	err := s.pool.QueryRow(ctx, `SELECT media_enabled FROM notifications.scope_state WHERE account_id=$1 AND principal_id=$2`, scope.Account.String(), scope.Actor.String()).Scan(&enabled)
	if errors.Is(err, pgx.ErrNoRows) {
		return true, nil
	}
	return enabled, storage.Failure(err)
}
func (s *Store) SetMediaEnabled(ctx context.Context, scope app.Scope, enabled bool) error {
	if !scope.Valid() {
		return app.ErrInput
	}
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if _, _, err := lockScope(ctx, tx, scope); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `UPDATE notifications.scope_state SET media_enabled=$3 WHERE account_id=$1 AND principal_id=$2`, scope.Account.String(), scope.Actor.String(), enabled)
		return err
	})
}
