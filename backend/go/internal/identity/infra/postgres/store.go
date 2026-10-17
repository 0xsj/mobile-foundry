package postgres

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	producer "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/events"
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
	owner, err := (id.Random{}).NewID()
	if err != nil {
		return nil, err
	}
	return &Store{pool, ids, owner}, nil
}

const principalColumns = "id::text,login,display_name,password_hash,created_at"

func restorePrincipal(row pgx.Row) (domain.Principal, error) {
	var v domain.PrincipalSnapshot
	var identity string
	if err := row.Scan(&identity, &v.Login, &v.DisplayName, &v.PasswordHash, &v.CreatedAt); err != nil {
		return domain.Principal{}, err
	}
	var err error
	v.ID, err = id.Parse(identity)
	if err != nil {
		return domain.Principal{}, domain.ErrState
	}
	v.CreatedAt = v.CreatedAt.UTC()
	return domain.RestorePrincipal(v)
}
func (s *Store) findPrincipal(ctx context.Context, column, value string) (domain.Principal, bool, error) {
	p, err := restorePrincipal(s.pool.QueryRow(ctx, "SELECT "+principalColumns+" FROM identity.principals WHERE "+column+"=$1", value))
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Principal{}, false, nil
	}
	if err != nil {
		return domain.Principal{}, false, storage.Failure(err)
	}
	return p, true, nil
}
func (s *Store) ByLogin(ctx context.Context, login string) (domain.Principal, bool, error) {
	return s.findPrincipal(ctx, "login", login)
}
func (s *Store) ByID(ctx context.Context, identity id.ID) (domain.Principal, bool, error) {
	return s.findPrincipal(ctx, "id", identity.String())
}
func (s *Store) append(ctx context.Context, tx pgx.Tx, f domain.Fact, metadata events.Metadata) error {
	m, err := producer.Message(f, metadata, s.ids)
	if err != nil {
		return domain.ErrState.WithCause(err)
	}
	data, err := json.Marshal(m)
	if err != nil {
		return domain.ErrState.WithCause(err)
	}
	_, err = tx.Exec(ctx, "INSERT INTO identity.outbox(event_id,stream_id,aggregate_version,envelope) VALUES($1,$2,$3,$4)", m.ID.String(), producer.Stream(f).String(), int64(m.Version), data)
	return err
}
func (s *Store) Register(ctx context.Context, p domain.Principal, metadata events.Metadata) error {
	v := p.Snapshot()
	if _, err := domain.RestorePrincipal(v); err != nil {
		return err
	}
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, "INSERT INTO identity.principals(id,login,display_name,password_hash,created_at) VALUES($1,$2,$3,$4,$5)", v.ID.String(), v.Login, v.DisplayName, v.PasswordHash, v.CreatedAt)
		if err != nil {
			var sql *pgconn.PgError
			if errors.As(err, &sql) && sql.Code == "23505" && sql.ConstraintName == "principals_login_key" {
				return app.ErrExists
			}
			return err
		}
		return s.append(ctx, tx, p.Fact(), metadata)
	})
}

const sessionColumns = "id::text,principal_id::text,generation,access_digest,refresh_digest,created_at,updated_at,access_expires_at,expires_at,revoked_at"

func restoreSession(row pgx.Row) (domain.Session, error) {
	var v domain.SessionSnapshot
	var identity, principalID string
	var generation int64
	var revoked *time.Time
	if err := row.Scan(&identity, &principalID, &generation, &v.AccessDigest, &v.RefreshDigest, &v.CreatedAt, &v.UpdatedAt, &v.AccessExpiresAt, &v.ExpiresAt, &revoked); err != nil {
		return domain.Session{}, err
	}
	var err error
	v.ID, err = id.Parse(identity)
	if err != nil {
		return domain.Session{}, domain.ErrState
	}
	v.PrincipalID, err = id.Parse(principalID)
	if err != nil {
		return domain.Session{}, domain.ErrState
	}
	v.Generation = uint64(generation)
	v.CreatedAt, v.UpdatedAt, v.AccessExpiresAt, v.ExpiresAt = v.CreatedAt.UTC(), v.UpdatedAt.UTC(), v.AccessExpiresAt.UTC(), v.ExpiresAt.UTC()
	if revoked != nil {
		v.RevokedAt = revoked.UTC()
	}
	return domain.RestoreSession(v)
}
func (s *Store) Issue(ctx context.Context, session domain.Session, metadata events.Metadata) error {
	v := session.Snapshot()
	if _, err := domain.RestoreSession(v); err != nil {
		return err
	}
	if v.Generation != 1 || !v.RevokedAt.IsZero() {
		return domain.ErrState
	}
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, "INSERT INTO identity.sessions(id,principal_id,generation,access_digest,refresh_digest,created_at,updated_at,access_expires_at,expires_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9)", v.ID.String(), v.PrincipalID.String(), int64(v.Generation), v.AccessDigest, v.RefreshDigest, v.CreatedAt, v.UpdatedAt, v.AccessExpiresAt, v.ExpiresAt)
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, "INSERT INTO identity.refresh_credentials(digest,session_id,generation) VALUES($1,$2,$3)", v.RefreshDigest, v.ID.String(), int64(v.Generation)); err != nil {
			return err
		}
		return s.append(ctx, tx, session.Fact("session.issued", ""), metadata)
	})
}
func loadRefresh(ctx context.Context, tx pgx.Tx, digest string) (domain.Session, uint64, error) {
	var identity string
	var generation int64
	if err := tx.QueryRow(ctx, "SELECT session_id::text,generation FROM identity.refresh_credentials WHERE digest=$1", digest).Scan(&identity, &generation); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.Session{}, 0, domain.ErrCredential
		}
		return domain.Session{}, 0, err
	}
	s, err := restoreSession(tx.QueryRow(ctx, "SELECT "+sessionColumns+" FROM identity.sessions WHERE id=$1 FOR UPDATE", identity))
	return s, uint64(generation), err
}
func (s *Store) save(ctx context.Context, tx pgx.Tx, next domain.Session, f domain.Fact, metadata events.Metadata) error {
	v := next.Snapshot()
	var revoked *time.Time
	if !v.RevokedAt.IsZero() {
		revoked = &v.RevokedAt
	}
	tag, err := tx.Exec(ctx, "UPDATE identity.sessions SET generation=$1,access_digest=$2,refresh_digest=$3,updated_at=$4,access_expires_at=$5,revoked_at=$6 WHERE id=$7 AND generation=$8", int64(v.Generation), v.AccessDigest, v.RefreshDigest, v.UpdatedAt, v.AccessExpiresAt, revoked, v.ID.String(), int64(v.Generation-1))
	if err != nil {
		return err
	}
	if tag.RowsAffected() != 1 {
		return domain.ErrState
	}
	if f.Action == "session.renewed" {
		if _, err := tx.Exec(ctx, "INSERT INTO identity.refresh_credentials(digest,session_id,generation) VALUES($1,$2,$3)", v.RefreshDigest, v.ID.String(), int64(v.Generation)); err != nil {
			return err
		}
	}
	return s.append(ctx, tx, f, metadata)
}
func (s *Store) Rotate(ctx context.Context, digest, access, refresh string, now time.Time, metadata events.Metadata) (domain.Session, bool, error) {
	var next domain.Session
	var reused bool
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		current, generation, err := loadRefresh(ctx, tx, digest)
		if err != nil {
			return err
		}
		if !current.Renewable(now) {
			return domain.ErrCredential
		}
		v := current.Snapshot()
		reused = generation != v.Generation || digest != v.RefreshDigest
		action, reason := "session.renewed", ""
		if reused {
			next, err = current.Revoke(now)
			action, reason = "session.revoked", "refresh_reuse"
		} else {
			next, err = current.Rotate(access, refresh, now)
		}
		if err != nil {
			return err
		}
		return s.save(ctx, tx, next, next.Fact(action, reason), metadata)
	})
	if err != nil {
		return domain.Session{}, false, err
	}
	return next, reused, nil
}
func (s *Store) Revoke(ctx context.Context, digest string, now time.Time, metadata events.Metadata) error {
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		current, _, err := loadRefresh(ctx, tx, digest)
		if err != nil {
			return err
		}
		if !current.Snapshot().RevokedAt.IsZero() {
			return nil
		}
		next, err := current.Revoke(now)
		if err != nil {
			return err
		}
		return s.save(ctx, tx, next, next.Fact("session.revoked", "sign_out"), metadata)
	})
}
func (s *Store) Access(ctx context.Context, digest string, now time.Time) (id.ID, error) {
	actor, err := s.AccessSession(ctx, digest, now)
	return actor.ID, err
}
func (s *Store) AccessSession(ctx context.Context, digest string, now time.Time) (principal.Principal, error) {
	session, err := restoreSession(s.pool.QueryRow(ctx, "SELECT "+sessionColumns+" FROM identity.sessions WHERE access_digest=$1", digest))
	if errors.Is(err, pgx.ErrNoRows) {
		return principal.Principal{}, domain.ErrCredential
	}
	if err != nil {
		return principal.Principal{}, storage.Failure(err)
	}
	if !session.Active(now) {
		return principal.Principal{}, domain.ErrCredential
	}
	v := session.Snapshot()
	return principal.Principal{ID: v.PrincipalID, SessionID: v.ID, SessionExpiresAt: v.ExpiresAt}, nil
}
