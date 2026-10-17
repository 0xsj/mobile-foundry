package postgres

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"time"
)

func (s *Store) Contact(ctx context.Context, actor id.ID) (domain.Contact, bool, error) {
	var c domain.Contact
	c.PrincipalID = actor
	err := s.pool.QueryRow(ctx, "SELECT email,verified_at FROM identity.contacts WHERE principal_id=$1", actor.String()).Scan(&c.Email, &c.VerifiedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Contact{}, false, nil
	}
	if err != nil {
		return domain.Contact{}, false, storage.Failure(err)
	}
	c.VerifiedAt = c.VerifiedAt.UTC()
	if err = c.Validate(); err != nil {
		return domain.Contact{}, false, err
	}
	return c, true, nil
}
func (s *Store) ByEmail(ctx context.Context, email string) (domain.Principal, bool, error) {
	var actor string
	err := s.pool.QueryRow(ctx, "SELECT principal_id::text FROM identity.contacts WHERE email=$1", email).Scan(&actor)
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Principal{}, false, nil
	}
	if err != nil {
		return domain.Principal{}, false, storage.Failure(err)
	}
	identity, err := id.Parse(actor)
	if err != nil {
		return domain.Principal{}, false, domain.ErrState
	}
	return s.ByID(ctx, identity)
}
func lockPrincipal(ctx context.Context, tx pgx.Tx, actor id.ID) (domain.Principal, error) {
	p, err := restorePrincipal(tx.QueryRow(ctx, "SELECT "+principalColumns+" FROM identity.principals WHERE id=$1 FOR UPDATE", actor.String()))
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Principal{}, domain.ErrCredential
	}
	return p, err
}
func (s *Store) BeginChallenge(ctx context.Context, c domain.Challenge) error {
	if err := c.Validate(); err != nil {
		return err
	}
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		p, err := lockPrincipal(ctx, tx, c.PrincipalID)
		if err != nil {
			return err
		}
		if p.Snapshot().PasswordHash != c.CredentialHash || c.CreatedAt.Before(p.Snapshot().CreatedAt) {
			return domain.ErrCredential
		}
		var existing string
		err = tx.QueryRow(ctx, "SELECT email FROM identity.contacts WHERE principal_id=$1", c.PrincipalID.String()).Scan(&existing)
		if err != nil && !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		if existing != "" && existing != c.Email {
			return app.ErrContact
		}
		if c.Purpose == domain.ResetPassword && existing != c.Email {
			return app.ErrChallenge
		}
		_, err = tx.Exec(ctx, `INSERT INTO identity.challenges(principal_id,purpose,email,digest,credential_hash,created_at,expires_at) VALUES($1,$2,$3,$4,$5,$6,$7) ON CONFLICT(principal_id,purpose) DO UPDATE SET email=excluded.email,digest=excluded.digest,credential_hash=excluded.credential_hash,created_at=excluded.created_at,expires_at=excluded.expires_at`, c.PrincipalID.String(), c.Purpose, c.Email, c.Digest, c.CredentialHash, c.CreatedAt, c.ExpiresAt)
		return err
	})
}

// Always lock the principal before sessions/challenges, matching checked issuance
// and both password mutations. The first token lookup is only a candidate.
func loadChallenge(ctx context.Context, tx pgx.Tx, digest, purpose string, now time.Time) (domain.Challenge, error) {
	var actor string
	err := tx.QueryRow(ctx, "SELECT principal_id::text FROM identity.challenges WHERE digest=$1 AND purpose=$2", digest, purpose).Scan(&actor)
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Challenge{}, app.ErrChallenge
	}
	if err != nil {
		return domain.Challenge{}, err
	}
	identity, err := id.Parse(actor)
	if err != nil {
		return domain.Challenge{}, domain.ErrState
	}
	p, err := lockPrincipal(ctx, tx, identity)
	if err != nil {
		return domain.Challenge{}, err
	}
	c := domain.Challenge{PrincipalID: identity, Purpose: purpose, Digest: digest}
	err = tx.QueryRow(ctx, "SELECT email,credential_hash,created_at,expires_at FROM identity.challenges WHERE principal_id=$1 AND purpose=$2 AND digest=$3 FOR UPDATE", actor, purpose, digest).Scan(&c.Email, &c.CredentialHash, &c.CreatedAt, &c.ExpiresAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Challenge{}, app.ErrChallenge
	}
	if err != nil {
		return domain.Challenge{}, err
	}
	c.CreatedAt = c.CreatedAt.UTC()
	c.ExpiresAt = c.ExpiresAt.UTC()
	if c.Validate() != nil {
		return domain.Challenge{}, domain.ErrState
	}
	if !c.Active(now) || p.Snapshot().PasswordHash != c.CredentialHash || c.CreatedAt.Before(p.Snapshot().CreatedAt) {
		return domain.Challenge{}, app.ErrChallenge
	}
	return c, nil
}
func (s *Store) lifecycle(ctx context.Context, tx pgx.Tx, actor id.ID, action string, now time.Time, m events.Metadata) error {
	var version int64
	if err := tx.QueryRow(ctx, "UPDATE identity.principals SET revision=revision+1 WHERE id=$1 RETURNING revision", actor.String()).Scan(&version); err != nil {
		return err
	}
	return s.append(ctx, tx, domain.Fact{PrincipalID: actor, Action: action, Version: uint64(version), OccurredAt: domain.Time(now)}, m)
}
func (s *Store) VerifyEmail(ctx context.Context, digest string, now time.Time, m events.Metadata) error {
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		c, err := loadChallenge(ctx, tx, digest, domain.VerifyEmail, now)
		if err != nil {
			return err
		}
		tag, err := tx.Exec(ctx, "INSERT INTO identity.contacts(principal_id,email,verified_at) VALUES($1,$2,$3) ON CONFLICT(principal_id) DO UPDATE SET verified_at=excluded.verified_at WHERE identity.contacts.email=excluded.email", c.PrincipalID.String(), c.Email, domain.Time(now))
		if err != nil {
			var pg *pgconn.PgError
			if errors.As(err, &pg) && pg.Code == "23505" && pg.ConstraintName == "contacts_email_key" {
				return app.ErrContact
			}
			return err
		}
		if tag.RowsAffected() != 1 {
			return app.ErrContact
		}
		if err = s.lifecycle(ctx, tx, c.PrincipalID, "email.verified", now, m); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, "DELETE FROM identity.challenges WHERE principal_id=$1 AND purpose=$2", c.PrincipalID.String(), domain.VerifyEmail)
		return err
	})
}
func (s *Store) ResetPassword(ctx context.Context, digest, hash string, now time.Time, m events.Metadata) error {
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		c, err := loadChallenge(ctx, tx, digest, domain.ResetPassword, now)
		if err != nil {
			return err
		}
		return s.change(ctx, tx, c.PrincipalID, c.CredentialHash, hash, now, m, "password_reset")
	})
}
func (s *Store) ChangePassword(ctx context.Context, actor id.ID, expected, hash string, now time.Time, m events.Metadata) error {
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if _, err := lockPrincipal(ctx, tx, actor); err != nil {
			return err
		}
		return s.change(ctx, tx, actor, expected, hash, now, m, "password_change")
	})
}
func (s *Store) change(ctx context.Context, tx pgx.Tx, actor id.ID, expected, hash string, now time.Time, m events.Metadata, reason string) error {
	p, err := lockPrincipal(ctx, tx, actor)
	if err != nil {
		return err
	}
	if p.Snapshot().PasswordHash != expected || now.Before(p.Snapshot().CreatedAt) {
		return domain.ErrCredential
	}
	if _, err = p.WithPassword(hash); err != nil {
		return err
	}
	rows, err := tx.Query(ctx, "SELECT "+sessionColumns+" FROM identity.sessions WHERE principal_id=$1 AND revoked_at IS NULL ORDER BY id FOR UPDATE", actor.String())
	if err != nil {
		return err
	}
	var sessions []domain.Session
	for rows.Next() {
		session, e := restoreSession(rows)
		if e != nil {
			rows.Close()
			return e
		}
		sessions = append(sessions, session)
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return err
	}
	for _, current := range sessions {
		next, err := current.Revoke(now)
		if err != nil {
			return err
		}
		if err = s.save(ctx, tx, next, next.Fact("session.revoked", reason), m); err != nil {
			return err
		}
	}
	if _, err = tx.Exec(ctx, "UPDATE identity.principals SET password_hash=$1 WHERE id=$2", hash, actor.String()); err != nil {
		return err
	}
	if _, err = tx.Exec(ctx, "DELETE FROM identity.challenges WHERE principal_id=$1", actor.String()); err != nil {
		return err
	}
	action := "password.changed"
	if reason == "password_reset" {
		action = "password.recovered"
	}
	return s.lifecycle(ctx, tx, actor, action, now, m)
}
func (s *Store) IssueChecked(ctx context.Context, session domain.Session, expected string, m events.Metadata) error {
	return s.issue(ctx, session, expected, m)
}
func (s *Store) PruneChallenges(ctx context.Context, now time.Time, limit int) (int, error) {
	if limit < 1 || limit > 1000 {
		return 0, domain.ErrInput
	}
	tag, err := s.pool.Exec(ctx, "DELETE FROM identity.challenges WHERE (principal_id,purpose) IN (SELECT principal_id,purpose FROM identity.challenges WHERE expires_at<=$1 ORDER BY expires_at LIMIT $2 FOR UPDATE SKIP LOCKED)", now, limit)
	return int(tag.RowsAffected()), storage.Failure(err)
}
