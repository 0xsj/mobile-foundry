package postgres

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/notes/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Store struct{ pool *pgxpool.Pool }

func New(pool *pgxpool.Pool) *Store { return &Store{pool: pool} }
func fingerprint(w domain.Write) [32]byte {
	// Keep the v1 encoding stable across private Go field/struct refactors.
	payload, _ := json.Marshal(struct {
		CommandID id.ID  `json:"command_id"`
		Action    string `json:"action"`
		NoteID    id.ID  `json:"note_id"`
		Title     string `json:"title"`
		Expected  uint64 `json:"expected_version"`
	}{w.CommandID, w.Action, w.NoteID, w.Title, w.Expected})
	return sha256.Sum256(append([]byte("notes.write.v1\n"), payload...))
}
func decode(wire []byte) (domain.Receipt, error) {
	var r domain.Receipt
	if json.Unmarshal(wire, &r) != nil || !r.Valid() {
		return r, domain.ErrState
	}
	return r, nil
}
func restore(row pgx.Row) (domain.Note, error) {
	var n domain.Note
	var identity string
	var title []byte
	var version int64
	if err := row.Scan(&identity, &title, &version); err != nil {
		return n, err
	}
	n = domain.Note{ID: id.ID(identity), Title: string(title), Version: uint64(version)}
	if !n.Valid() {
		return n, domain.ErrState
	}
	return n, nil
}
func (s *Store) Apply(ctx context.Context, account, actor id.ID, w domain.Write, candidate id.ID) (domain.Receipt, error) {
	if !account.Valid() || !actor.Valid() {
		return domain.Receipt{}, id.ErrInvalid
	}
	if err := w.Validate(); err != nil {
		return domain.Receipt{}, err
	}
	hash := fingerprint(w)
	var result domain.Receipt
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `INSERT INTO notes.receipts(account_id,principal_id,command_id,fingerprint) VALUES($1,$2,$3,$4) ON CONFLICT DO NOTHING`, account.String(), actor.String(), w.CommandID.String(), hash[:])
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			// A separate ReadCommitted statement observes a competing committed claim.
			var savedHash, wire []byte
			if err := tx.QueryRow(ctx, `SELECT fingerprint,result FROM notes.receipts WHERE account_id=$1 AND principal_id=$2 AND command_id=$3`, account.String(), actor.String(), w.CommandID.String()).Scan(&savedHash, &wire); err != nil {
				return err
			}
			if !bytes.Equal(savedHash, hash[:]) {
				return domain.ErrKey
			}
			result, err = decode(wire)
			if err != nil {
				return err
			}
			if result.AccountID != account || result.PrincipalID != actor || result.CommandID != w.CommandID {
				return domain.ErrState
			}
			return nil
		}
		var current *domain.Note
		if w.Action != domain.Create {
			n, err := restore(tx.QueryRow(ctx, `SELECT id::text,title,version FROM notes.records WHERE account_id=$1 AND id=$2 FOR UPDATE`, account.String(), w.NoteID.String()))
			if err != nil && !errors.Is(err, pgx.ErrNoRows) {
				return err
			}
			if err == nil {
				current = &n
			}
		}
		result, err = domain.Decide(w, current, candidate, account, actor)
		if err != nil {
			return err
		}
		switch w.Action {
		case domain.Create:
			_, err = tx.Exec(ctx, `INSERT INTO notes.records(account_id,id,title,version) VALUES($1,$2,$3,1)`, account.String(), result.NoteID.String(), []byte(result.Title))
		case domain.Update:
			_, err = tx.Exec(ctx, `UPDATE notes.records SET title=$3,version=$4 WHERE account_id=$1 AND id=$2`, account.String(), result.NoteID.String(), []byte(result.Title), int64(result.Version))
		case domain.Delete:
			_, err = tx.Exec(ctx, `DELETE FROM notes.records WHERE account_id=$1 AND id=$2`, account.String(), result.NoteID.String())
		}
		if err != nil {
			var sql *pgconn.PgError
			if errors.As(err, &sql) && sql.Code == "23505" && sql.ConstraintName == "records_pkey" {
				return domain.ErrConflict
			}
			return err
		}
		wire, err := json.Marshal(result)
		if err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `UPDATE notes.receipts SET result=$4 WHERE account_id=$1 AND principal_id=$2 AND command_id=$3`, account.String(), actor.String(), w.CommandID.String(), wire)
		return err
	})
	if err != nil {
		return domain.Receipt{}, err
	}
	return result, nil
}
func (s *Store) Receipt(ctx context.Context, account, actor, command id.ID) (domain.Receipt, bool, error) {
	var wire []byte
	err := s.pool.QueryRow(ctx, `SELECT result FROM notes.receipts WHERE account_id=$1 AND principal_id=$2 AND command_id=$3`, account.String(), actor.String(), command.String()).Scan(&wire)
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Receipt{}, false, nil
	}
	if err != nil {
		return domain.Receipt{}, false, storage.Failure(err)
	}
	r, err := decode(wire)
	return r, err == nil, err
}
func (s *Store) Get(ctx context.Context, account, note id.ID) (domain.Note, bool, error) {
	n, err := restore(s.pool.QueryRow(ctx, `SELECT id::text,title,version FROM notes.records WHERE account_id=$1 AND id=$2`, account.String(), note.String()))
	if errors.Is(err, pgx.ErrNoRows) {
		return n, false, nil
	}
	if err != nil {
		return n, false, storage.Failure(err)
	}
	return n, true, nil
}
func (s *Store) List(ctx context.Context, account, after id.ID, limit int) ([]domain.Note, error) {
	if limit < 1 || limit > 101 {
		return nil, domain.ErrWrite
	}
	floor := after.String()
	if floor == "" {
		floor = "00000000-0000-0000-0000-000000000000"
	}
	rows, err := s.pool.Query(ctx, `SELECT id::text,title,version FROM notes.records WHERE account_id=$1 AND id>$2 ORDER BY id LIMIT $3`, account.String(), floor, limit)
	if err != nil {
		return nil, storage.Failure(err)
	}
	defer rows.Close()
	result := make([]domain.Note, 0)
	for rows.Next() {
		n, err := restore(rows)
		if err != nil {
			return nil, storage.Failure(err)
		}
		result = append(result, n)
	}
	if err := rows.Err(); err != nil {
		return nil, storage.Failure(err)
	}
	return result, nil
}
