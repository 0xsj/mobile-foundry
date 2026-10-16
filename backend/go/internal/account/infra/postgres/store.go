package postgres

import (
	"context"
	"encoding/json"
	"errors"

	"github.com/0xsj/mobile-foundry/backend/internal/account/app"
	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	producer "github.com/0xsj/mobile-foundry/backend/internal/account/infra/events"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// One Store owns one dispatcher. Different processes use distinct lease owners.
// Claim one record per poll so the five-second lease covers the two-second delivery budget.
type Store struct {
	pool  *pgxpool.Pool
	ids   id.Generator
	owner id.ID
}

func New(pool *pgxpool.Pool, ids id.Generator) (*Store, error) {
	owner, err := (id.Random{}).NewID()
	if err != nil {
		return nil, err
	}
	return &Store{pool: pool, ids: ids, owner: owner}, nil
}

const columns = "id::text, owner_id::text, name, version, created_at, updated_at"

func restore(row pgx.Row) (domain.Account, error) {
	var snapshot domain.Snapshot
	var identity, owner string
	var version int64
	if err := row.Scan(&identity, &owner, &snapshot.Name, &version, &snapshot.CreatedAt, &snapshot.UpdatedAt); err != nil {
		return domain.Account{}, err
	}
	var err error
	snapshot.ID, err = id.Parse(identity)
	if err != nil {
		return domain.Account{}, domain.ErrState
	}
	snapshot.OwnerID, err = id.Parse(owner)
	if err != nil {
		return domain.Account{}, domain.ErrState
	}
	snapshot.Version = uint64(version)
	snapshot.CreatedAt, snapshot.UpdatedAt = snapshot.CreatedAt.UTC(), snapshot.UpdatedAt.UTC()
	return domain.Restore(snapshot)
}
func (s *Store) Get(ctx context.Context, identity id.ID) (domain.Account, bool, error) {
	account, err := restore(s.pool.QueryRow(ctx, "SELECT "+columns+" FROM account.accounts WHERE id=$1", identity.String()))
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Account{}, false, nil
	}
	if err != nil {
		return domain.Account{}, false, storage.Failure(err)
	}
	return account, true, nil
}
func (s *Store) Create(ctx context.Context, account domain.Account, fact domain.Fact, metadata events.Metadata) error {
	return s.commit(ctx, account, 0, fact, metadata)
}
func (s *Store) Rename(ctx context.Context, account domain.Account, expected uint64, fact domain.Fact, metadata events.Metadata) error {
	return s.commit(ctx, account, expected, fact, metadata)
}
func (s *Store) commit(ctx context.Context, account domain.Account, expected uint64, fact domain.Fact, metadata events.Metadata) error {
	snapshot := account.Snapshot()
	if _, err := domain.Restore(snapshot); err != nil {
		return err
	}
	action := domain.Created
	if expected != 0 {
		action = domain.Renamed
	}
	required, err := account.Fact(action, snapshot.OwnerID)
	if err != nil || required != fact || snapshot.Version != expected+1 {
		return domain.ErrState
	}
	message, err := producer.Message(fact, metadata, s.ids)
	if err != nil {
		return domain.ErrState.WithCause(err)
	}
	payload, err := json.Marshal(message)
	if err != nil {
		return domain.ErrState.WithCause(err)
	}
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if expected == 0 {
			_, err := tx.Exec(ctx, "INSERT INTO account.accounts(id,owner_id,name,version,created_at,updated_at) VALUES($1,$2,$3,$4,$5,$6)", snapshot.ID.String(), snapshot.OwnerID.String(), snapshot.Name, int64(snapshot.Version), snapshot.CreatedAt, snapshot.UpdatedAt)
			if err != nil {
				var sql *pgconn.PgError
				if errors.As(err, &sql) && sql.Code == "23505" && sql.ConstraintName == "accounts_pkey" {
					return app.ErrConflict
				}
				return err
			}
		} else {
			current, err := restore(tx.QueryRow(ctx, "SELECT "+columns+" FROM account.accounts WHERE id=$1 FOR UPDATE", snapshot.ID.String()))
			if errors.Is(err, pgx.ErrNoRows) {
				return app.ErrNotFound
			}
			if err != nil {
				return err
			}
			prior := current.Snapshot()
			if prior.Version != expected {
				return app.ErrConflict
			}
			if prior.OwnerID != snapshot.OwnerID || prior.CreatedAt != snapshot.CreatedAt || snapshot.UpdatedAt.Before(prior.UpdatedAt) {
				return domain.ErrState
			}
			tag, err := tx.Exec(ctx, "UPDATE account.accounts SET name=$1,version=$2,updated_at=$3 WHERE id=$4 AND version=$5", snapshot.Name, int64(snapshot.Version), snapshot.UpdatedAt, snapshot.ID.String(), int64(expected))
			if err != nil {
				return err
			}
			if tag.RowsAffected() != 1 {
				return app.ErrConflict
			}
		}
		_, err := tx.Exec(ctx, "INSERT INTO account.outbox(event_id,account_id,aggregate_version,envelope) VALUES($1,$2,$3,$4)", message.ID.String(), message.AccountID.String(), int64(message.Version), payload)
		return err // Any outbox failure rolls the account write back.
	})
}

func (s *Store) Pending(ctx context.Context, limit int) ([]events.Envelope, error) {
	if limit < 1 {
		return nil, events.ErrInvalid
	}
	result := make([]events.Envelope, 0, 1)
	var corrupt bool
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		var identity string
		var accountID string
		var version int64
		var payload []byte
		err := tx.QueryRow(ctx, `SELECT event_id::text, account_id::text, aggregate_version, envelope FROM account.outbox candidate
		 WHERE delivered_at IS NULL AND NOT dead AND (lease_until IS NULL OR lease_until <= clock_timestamp())
		 AND NOT EXISTS (SELECT 1 FROM account.outbox prior WHERE prior.account_id=candidate.account_id AND prior.sequence<candidate.sequence AND prior.delivered_at IS NULL)
		 ORDER BY sequence FOR UPDATE SKIP LOCKED LIMIT 1`).Scan(&identity, &accountID, &version, &payload)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		var message events.Envelope
		if err := json.Unmarshal(payload, &message); err != nil {
			corrupt = true
		}
		if err := message.Validate(); err != nil || message.ID.String() != identity || message.AccountID.String() != accountID || message.Version != uint64(version) {
			corrupt = true
		}
		if corrupt {
			_, err := tx.Exec(ctx, "UPDATE account.outbox SET dead=true,failure_code='invalid_envelope',lease_owner=NULL,lease_until=NULL WHERE event_id=$1", identity)
			return err
		}
		_, err = tx.Exec(ctx, "UPDATE account.outbox SET lease_owner=$1,lease_until=clock_timestamp()+interval '5 seconds' WHERE event_id=$2", s.owner.String(), identity)
		if err != nil {
			return err
		}
		result = append(result, message)
		return nil
	})
	if err != nil {
		return nil, err
	}
	if corrupt {
		return nil, domain.ErrState
	}
	return result, nil
}
func (s *Store) Delivered(ctx context.Context, identity id.ID) error {
	tag, err := s.pool.Exec(ctx, "UPDATE account.outbox SET delivered_at=clock_timestamp(),lease_owner=NULL,lease_until=NULL WHERE event_id=$1 AND lease_owner=$2 AND delivered_at IS NULL AND NOT dead", identity.String(), s.owner.String())
	if err != nil {
		return storage.Failure(err)
	}
	if tag.RowsAffected() != 1 {
		return events.ErrInvalid
	}
	return nil
}
func (s *Store) Failed(ctx context.Context, identity id.ID, code string, maximum int) error {
	if maximum < 1 || code == "" || len(code) > 128 {
		return events.ErrInvalid
	}
	tag, err := s.pool.Exec(ctx, "UPDATE account.outbox SET attempts=attempts+1,failure_code=$3,dead=(attempts+1 >= $4),lease_owner=NULL,lease_until=NULL WHERE event_id=$1 AND lease_owner=$2 AND delivered_at IS NULL AND NOT dead", identity.String(), s.owner.String(), code, maximum)
	if err != nil {
		return storage.Failure(err)
	}
	if tag.RowsAffected() != 1 {
		return events.ErrInvalid
	}
	return nil
}
func (s *Store) Ready(ctx context.Context) error {
	var dead bool
	if err := s.pool.QueryRow(ctx, "SELECT EXISTS(SELECT 1 FROM account.outbox WHERE dead)").Scan(&dead); err != nil {
		return storage.Failure(err)
	}
	if dead {
		return apperrors.New(apperrors.Unavailable, "account.delivery_exhausted", "account event delivery exhausted retries").WithPublic("Account event delivery needs attention.")
	}
	return nil
}
