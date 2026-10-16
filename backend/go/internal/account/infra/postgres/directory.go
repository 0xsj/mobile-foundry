package postgres

import (
	"context"
	"encoding/json"
	"errors"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	producer "github.com/0xsj/mobile-foundry/backend/internal/account/infra/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

func (s *Store) Bootstrap(ctx context.Context, candidate domain.Account, fact domain.Fact, metadata events.Metadata) (domain.Account, bool, error) {
	snapshot := candidate.Snapshot()
	if _, err := domain.Restore(snapshot); err != nil {
		return domain.Account{}, false, err
	}
	required, err := candidate.Fact(domain.Created, snapshot.OwnerID)
	if err != nil || fact != required {
		return domain.Account{}, false, domain.ErrState
	}
	message, err := producer.Message(fact, metadata, s.ids)
	if err != nil {
		return domain.Account{}, false, domain.ErrState.WithCause(err)
	}
	payload, err := json.Marshal(message)
	if err != nil {
		return domain.Account{}, false, domain.ErrState.WithCause(err)
	}
	var result domain.Account
	var created bool
	err = storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		var claimed string
		// The owner key chooses one winner. The owned FK is checked at commit,
		// after the winner has inserted the aggregate and its required fact.
		err := tx.QueryRow(ctx, `INSERT INTO account.personal_accounts(owner_id,account_id) VALUES($1,$2)
		 ON CONFLICT(owner_id) DO NOTHING RETURNING account_id::text`, snapshot.OwnerID.String(), snapshot.ID.String()).Scan(&claimed)
		if errors.Is(err, pgx.ErrNoRows) {
			// A separate statement under Read Committed sees the winner after
			// ON CONFLICT waited for its transaction. A single-statement CTE may not.
			result, err = restore(tx.QueryRow(ctx, "SELECT "+columns+" FROM account.accounts WHERE id=(SELECT account_id FROM account.personal_accounts WHERE owner_id=$1)", snapshot.OwnerID.String()))
			if errors.Is(err, pgx.ErrNoRows) || (err == nil && !result.OwnedBy(snapshot.OwnerID)) {
				return domain.ErrState
			}
			return err
		}
		if err != nil {
			return err
		}
		_, err = tx.Exec(ctx, "INSERT INTO account.accounts(id,owner_id,name,version,created_at,updated_at) VALUES($1,$2,$3,$4,$5,$6)", snapshot.ID.String(), snapshot.OwnerID.String(), snapshot.Name, int64(snapshot.Version), snapshot.CreatedAt, snapshot.UpdatedAt)
		if err != nil {
			var sql *pgconn.PgError
			if errors.As(err, &sql) && sql.Code == "23505" && sql.ConstraintName == "accounts_pkey" {
				return app.ErrConflict
			}
			return err
		}
		if _, err := tx.Exec(ctx, "INSERT INTO account.outbox(event_id,account_id,aggregate_version,envelope) VALUES($1,$2,$3,$4)", message.ID.String(), message.AccountID.String(), int64(message.Version), payload); err != nil {
			return err
		}
		result, created = candidate, true
		return nil
	})
	if err != nil {
		return domain.Account{}, false, err
	}
	return result, created, nil
}

func (s *Store) List(ctx context.Context, owner, after id.ID, limit int) ([]domain.Account, error) {
	if !owner.Valid() || (after != "" && !after.Valid()) || limit < 1 || limit > 101 {
		return nil, app.ErrInput
	}
	// A valid nonzero UUID sorts after the sentinel used for a first page.
	position := after.String()
	if after == "" {
		position = "00000000-0000-0000-0000-000000000000"
	}
	rows, err := s.pool.Query(ctx, "SELECT "+columns+" FROM account.accounts WHERE owner_id=$1 AND id>$2::uuid ORDER BY id LIMIT $3", owner.String(), position, limit)
	if err != nil {
		return nil, storage.Failure(err)
	}
	defer rows.Close()
	result := make([]domain.Account, 0)
	for rows.Next() {
		account, err := restore(rows)
		if err != nil {
			return nil, storage.Failure(err)
		}
		result = append(result, account)
	}
	if err := rows.Err(); err != nil {
		return nil, storage.Failure(err)
	}
	return result, nil
}
