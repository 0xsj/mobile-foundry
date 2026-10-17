package postgres

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Store struct{ pool *pgxpool.Pool }

var errState = apperrors.New(apperrors.Internal, "audit.invalid_state", "invalid persisted audit evidence")

func New(pool *pgxpool.Pool) *Store { return &Store{pool: pool} }
func fingerprint(evidence domain.Evidence) (string, error) {
	data, err := json.Marshal(evidence)
	if err != nil {
		return "", domain.ErrEntry.WithCause(err)
	}
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:]), nil
}
func (s *Store) Record(ctx context.Context, consumer string, entry domain.Entry) error {
	if consumer == "" {
		return domain.ErrEntry
	}
	evidence := entry.Evidence()
	if _, err := domain.New(evidence, entry.RecordedAt()); err != nil {
		return err
	}
	digest, err := fingerprint(evidence)
	if err != nil {
		return err
	}
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		var sequence int64
		if err := tx.QueryRow(ctx, "SELECT next_sequence FROM audit.append_state WHERE singleton FOR UPDATE").Scan(&sequence); err != nil {
			return err
		}
		var prior string
		err := tx.QueryRow(ctx, "SELECT fingerprint FROM audit.inbox WHERE consumer=$1 AND event_id=$2", consumer, evidence.EventID.String()).Scan(&prior)
		if err == nil {
			if prior != digest {
				return app.ErrReplay
			}
			return nil
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		if sequence >= 1<<53-1 {
			return domain.ErrEntry
		}
		_, err = tx.Exec(ctx, `INSERT INTO audit.entries(sequence,event_id,account_id,actor_id,action,version,occurred_at,recorded_at,request_id,correlation_id,principal_id,session_id,reason,file_id)
		 VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`, sequence, evidence.EventID.String(), nullable(evidence.AccountID), evidence.ActorID.String(), evidence.Action, int64(evidence.Version), evidence.OccurredAt, entry.RecordedAt(), evidence.RequestID, evidence.CorrelationID, nullable(evidence.PrincipalID), nullable(evidence.SessionID), evidence.Reason, nullable(evidence.FileID))
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, "INSERT INTO audit.inbox(consumer,event_id,evidence_sequence,fingerprint) VALUES($1,$2,$3,$4)", consumer, evidence.EventID.String(), sequence, digest); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, "UPDATE audit.append_state SET next_sequence=$1 WHERE singleton", sequence+1)
		return err
	})
}
func (s *Store) List(ctx context.Context, accountID id.ID, after uint64, limit int) (app.Page, error) {
	return s.list(ctx, "account_id", accountID, after, limit)
}
func (s *Store) ListPrincipal(ctx context.Context, identity id.ID, after uint64, limit int) (app.Page, error) {
	return s.list(ctx, "principal_id", identity, after, limit)
}
func nullable(identity id.ID) any {
	if identity == "" {
		return nil
	}
	return identity.String()
}
func parseOptional(value *string) (id.ID, error) {
	if value == nil {
		return "", nil
	}
	return id.Parse(*value)
}
func (s *Store) list(ctx context.Context, column string, identity id.ID, after uint64, limit int) (app.Page, error) {
	if limit < 1 || limit > 100 || after > 1<<53-1 {
		return app.Page{}, app.ErrInput
	}
	rows, err := s.pool.Query(ctx, `SELECT sequence,event_id::text,account_id::text,actor_id::text,action,version,occurred_at,recorded_at,request_id,correlation_id,principal_id::text,session_id::text,reason,file_id::text
	 FROM audit.entries WHERE `+column+`=$1 AND sequence>$2 ORDER BY sequence LIMIT $3`, identity.String(), int64(after), limit+1)
	if err != nil {
		return app.Page{}, storage.Failure(err)
	}
	defer rows.Close()
	page := app.Page{Records: make([]app.Record, 0)}
	for rows.Next() {
		var sequence, version int64
		var eventID, actor string
		var account, principalID, sessionID, fileID *string
		var e domain.Evidence
		var recordedAt time.Time
		if err := rows.Scan(&sequence, &eventID, &account, &actor, &e.Action, &version, &e.OccurredAt, &recordedAt, &e.RequestID, &e.CorrelationID, &principalID, &sessionID, &e.Reason, &fileID); err != nil {
			return app.Page{}, storage.Failure(err)
		}
		e.EventID, err = id.Parse(eventID)
		if err != nil {
			return app.Page{}, errState
		}
		e.AccountID, err = parseOptional(account)
		if err != nil {
			return app.Page{}, errState
		}
		e.ActorID, err = id.Parse(actor)
		if err != nil {
			return app.Page{}, errState
		}
		e.Version, e.OccurredAt = uint64(version), e.OccurredAt.UTC()
		e.PrincipalID, err = parseOptional(principalID)
		if err != nil {
			return app.Page{}, errState
		}
		e.SessionID, err = parseOptional(sessionID)
		if err != nil {
			return app.Page{}, errState
		}
		e.FileID, err = parseOptional(fileID)
		if err != nil {
			return app.Page{}, errState
		}
		entry, err := domain.New(e, recordedAt.UTC())
		if err != nil {
			return app.Page{}, errState.WithCause(err)
		}
		if len(page.Records) == limit {
			page.Next = page.Records[limit-1].Sequence
			break
		}
		page.Records = append(page.Records, app.Record{Sequence: uint64(sequence), Entry: entry})
	}
	if err := rows.Err(); err != nil {
		return app.Page{}, storage.Failure(err)
	}
	return page, nil
}
