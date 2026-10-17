package postgres

import (
	"context"
	"encoding/json"
	"errors"

	"github.com/0xsj/mobile-foundry/backend/internal/files/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"

	"github.com/jackc/pgx/v5"
)

func (s *Store) Pending(ctx context.Context, limit int) ([]events.Envelope, error) {
	if limit < 1 {
		return nil, events.ErrInvalid
	}
	result := []events.Envelope{}
	var corrupt bool
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		var identity string
		var version int64
		var data []byte
		err := tx.QueryRow(ctx, `SELECT event_id::text,aggregate_version,envelope FROM files.outbox candidate
 WHERE delivered_at IS NULL AND NOT dead AND (lease_until IS NULL OR lease_until<=clock_timestamp())
 AND NOT EXISTS(SELECT 1 FROM files.outbox prior WHERE prior.stream_id=candidate.stream_id AND prior.sequence<candidate.sequence AND prior.delivered_at IS NULL)
 ORDER BY sequence FOR UPDATE SKIP LOCKED LIMIT 1`).Scan(&identity, &version, &data)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		var m events.Envelope
		if json.Unmarshal(data, &m) != nil {
			corrupt = true
		}
		if m.Validate() != nil || m.ID.String() != identity || m.Version != uint64(version) || m.Producer != "files" || !m.AccountID.Valid() {
			corrupt = true
		}
		if corrupt {
			_, err := tx.Exec(ctx, "UPDATE files.outbox SET dead=true,failure_code='invalid_envelope',lease_owner=NULL,lease_until=NULL WHERE event_id=$1", identity)
			return err
		}
		if _, err := tx.Exec(ctx, "UPDATE files.outbox SET lease_owner=$1,lease_until=clock_timestamp()+interval '5 seconds' WHERE event_id=$2", s.owner.String(), identity); err != nil {
			return err
		}
		result = append(result, m)
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
	tag, err := s.pool.Exec(ctx, "UPDATE files.outbox SET delivered_at=clock_timestamp(),lease_owner=NULL,lease_until=NULL WHERE event_id=$1 AND lease_owner=$2 AND delivered_at IS NULL AND NOT dead", identity.String(), s.owner.String())
	if err != nil {
		return storage.Failure(err)
	}
	if tag.RowsAffected() != 1 {
		return events.ErrInvalid
	}
	return nil
}
func (s *Store) Failed(ctx context.Context, identity id.ID, code string, max int) error {
	if max < 1 || len(code) == 0 || len(code) > 128 {
		return events.ErrInvalid
	}
	tag, err := s.pool.Exec(ctx, "UPDATE files.outbox SET attempts=attempts+1,failure_code=$3,dead=(attempts+1 >= $4),lease_owner=NULL,lease_until=NULL WHERE event_id=$1 AND lease_owner=$2 AND delivered_at IS NULL AND NOT dead", identity.String(), s.owner.String(), code, max)
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
	if err := s.pool.QueryRow(ctx, "SELECT EXISTS(SELECT 1 FROM files.outbox WHERE dead)").Scan(&dead); err != nil {
		return storage.Failure(err)
	}
	if dead {
		return apperrors.New(apperrors.Unavailable, "files.delivery_exhausted", "files delivery needs attention")
	}
	return nil
}
