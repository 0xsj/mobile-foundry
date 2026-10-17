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
	"time"
)

func lockInstallations(ctx context.Context, tx pgx.Tx) error {
	var guard bool
	return tx.QueryRow(ctx, `SELECT singleton FROM notifications.installation_guard WHERE singleton FOR UPDATE`).Scan(&guard)
}
func installationActor(ctx context.Context, tx pgx.Tx, a app.InstallationActor, now time.Time) error {
	if err := a.Validate(now); err != nil {
		return err
	}
	var revoked bool
	if err := tx.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM notifications.revoked_installation_sessions WHERE principal_id=$1 AND session_id=$2)`, a.Principal.String(), a.Session.String()).Scan(&revoked); err != nil {
		return err
	}
	if revoked {
		return app.ErrInstallationSession
	}
	return nil
}

const installationColumns = `id::text,principal_id::text,session_id::text,registration_id::text,proof_digest,provider,application,environment,token,token_key,version,enabled,created_at,updated_at,expires_at`

func restoreInstallation(row pgx.Row) (domain.Installation, error) {
	var v domain.InstallationSnapshot
	var identity, principal, session, registration string
	var version int64
	var key *string
	if err := row.Scan(&identity, &principal, &session, &registration, &v.ProofDigest, &v.Spec.Route.Provider, &v.Spec.Route.Application, &v.Spec.Route.Environment, &v.Spec.Token, &key, &version, &v.Enabled, &v.CreatedAt, &v.UpdatedAt, &v.ExpiresAt); err != nil {
		return domain.Installation{}, err
	}
	var err error
	if v.ID, err = id.Parse(identity); err != nil {
		return domain.Installation{}, domain.ErrState
	}
	if v.PrincipalID, err = id.Parse(principal); err != nil {
		return domain.Installation{}, domain.ErrState
	}
	if v.SessionID, err = id.Parse(session); err != nil {
		return domain.Installation{}, domain.ErrState
	}
	if v.RegistrationID, err = id.Parse(registration); err != nil {
		return domain.Installation{}, domain.ErrState
	}
	v.Version = uint64(version)
	v.CreatedAt = v.CreatedAt.UTC()
	v.UpdatedAt = v.UpdatedAt.UTC()
	v.ExpiresAt = v.ExpiresAt.UTC()
	if v.Enabled && (key == nil || *key != records.InstallationTarget(v.Spec)) || !v.Enabled && key != nil {
		return domain.Installation{}, domain.ErrState
	}
	return domain.RestoreInstallation(v)
}
func findInstallation(ctx context.Context, tx pgx.Tx, identity id.ID, proof string) (domain.Installation, error) {
	i, err := restoreInstallation(tx.QueryRow(ctx, `SELECT `+installationColumns+` FROM notifications.installations WHERE id=$1`, identity.String()))
	if errors.Is(err, pgx.ErrNoRows) {
		return i, app.ErrInstallationMissing
	}
	if err != nil {
		return i, err
	}
	if !records.ProofMatches(i.Snapshot().ProofDigest, proof) {
		return domain.Installation{}, app.ErrInstallationMissing
	}
	return i, nil
}
func saveInstallation(ctx context.Context, tx pgx.Tx, i domain.Installation) error {
	v := i.Snapshot()
	var key any
	if v.Enabled {
		key = records.InstallationTarget(v.Spec)
	}
	_, err := tx.Exec(ctx, `INSERT INTO notifications.installations(id,principal_id,session_id,registration_id,proof_digest,provider,application,environment,token,token_key,version,enabled,created_at,updated_at,expires_at)
 VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15) ON CONFLICT(id) DO UPDATE SET principal_id=EXCLUDED.principal_id,session_id=EXCLUDED.session_id,registration_id=EXCLUDED.registration_id,provider=EXCLUDED.provider,application=EXCLUDED.application,environment=EXCLUDED.environment,token=EXCLUDED.token,token_key=EXCLUDED.token_key,version=EXCLUDED.version,enabled=EXCLUDED.enabled,updated_at=EXCLUDED.updated_at,expires_at=EXCLUDED.expires_at`, v.ID.String(), v.PrincipalID.String(), v.SessionID.String(), v.RegistrationID.String(), v.ProofDigest, v.Spec.Route.Provider, v.Spec.Route.Application, v.Spec.Route.Environment, v.Spec.Token, key, int64(v.Version), v.Enabled, v.CreatedAt, v.UpdatedAt, v.ExpiresAt)
	return err
}
func (s *Store) RegisterInstallation(ctx context.Context, m app.InstallationMutation, now time.Time) (domain.Installation, error) {
	if err := m.Validate(now); err != nil {
		return domain.Installation{}, err
	}
	var next domain.Installation
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockInstallations(ctx, tx); err != nil {
			return err
		}
		if err := installationActor(ctx, tx, m.Actor, now); err != nil {
			return err
		}
		prior, err := findInstallation(ctx, tx, m.ID, m.ProofDigest)
		if errors.Is(err, app.ErrInstallationMissing) {
			var exists bool
			if err := tx.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM notifications.installations WHERE id=$1)`, m.ID.String()).Scan(&exists); err != nil {
				return err
			}
			if exists || m.Expected != 0 {
				return app.ErrInstallationMissing
			}
			next, err = domain.NewInstallation(m.ID, m.Actor.Principal, m.Actor.Session, m.RegistrationID, m.ProofDigest, m.Spec, now, m.Actor.ExpiresAt)
		} else if err == nil {
			v := prior.Snapshot()
			if m.Expected == 0 || v.Version != m.Expected || v.RegistrationID == m.RegistrationID {
				return app.ErrInstallationConflict
			}
			next, err = prior.Bind(m.Actor.Principal, m.Actor.Session, m.RegistrationID, m.Spec, now, m.Actor.ExpiresAt)
		}
		if err != nil {
			return err
		}
		other, err := restoreInstallation(tx.QueryRow(ctx, `SELECT `+installationColumns+` FROM notifications.installations WHERE token_key=$1 AND id<>$2`, records.InstallationTarget(m.Spec), m.ID.String()))
		if err == nil {
			if now.Before(other.Snapshot().ExpiresAt) {
				return app.ErrInstallationTarget
			}
			retired, err := other.Disable(now)
			if err != nil {
				return err
			}
			if err := saveInstallation(ctx, tx, retired); err != nil {
				return err
			}
		} else if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		return saveInstallation(ctx, tx, next)
	})
	return next, err
}
func (s *Store) GetInstallation(ctx context.Context, a app.InstallationActor, identity id.ID, proof string, now time.Time) (domain.Installation, error) {
	var i domain.Installation
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockInstallations(ctx, tx); err != nil {
			return err
		}
		if err := installationActor(ctx, tx, a, now); err != nil {
			return err
		}
		var err error
		i, err = findInstallation(ctx, tx, identity, proof)
		return err
	})
	return i, err
}
func (s *Store) UnregisterInstallation(ctx context.Context, a app.InstallationActor, identity id.ID, proof string, expected uint64, now time.Time) (domain.Installation, error) {
	var result domain.Installation
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockInstallations(ctx, tx); err != nil {
			return err
		}
		if err := installationActor(ctx, tx, a, now); err != nil {
			return err
		}
		prior, err := findInstallation(ctx, tx, identity, proof)
		if err != nil {
			return err
		}
		v := prior.Snapshot()
		if v.PrincipalID != a.Principal || v.SessionID != a.Session {
			return app.ErrInstallationBinding
		}
		if !v.Enabled {
			result = prior
			return nil
		}
		if v.Version != expected {
			return app.ErrInstallationConflict
		}
		result, err = prior.Disable(now)
		if err != nil {
			return err
		}
		return saveInstallation(ctx, tx, result)
	})
	return result, err
}
func (s *Store) RetireSession(ctx context.Context, r app.SessionRevocation) error {
	if err := r.Validate(); err != nil {
		return err
	}
	hash := records.RevocationFingerprint(r)
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockInstallations(ctx, tx); err != nil {
			return err
		}
		var prior string
		err := tx.QueryRow(ctx, `SELECT fingerprint FROM notifications.installation_inbox WHERE event_id=$1`, r.EventID.String()).Scan(&prior)
		if err == nil {
			if prior != hash {
				return app.ErrReplay
			}
			return nil
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO notifications.revoked_installation_sessions(principal_id,session_id) VALUES($1,$2) ON CONFLICT DO NOTHING`, r.PrincipalID.String(), r.SessionID.String()); err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `SELECT `+installationColumns+` FROM notifications.installations WHERE principal_id=$1 AND session_id=$2 AND enabled`, r.PrincipalID.String(), r.SessionID.String())
		if err != nil {
			return err
		}
		items := []domain.Installation{}
		for rows.Next() {
			i, err := restoreInstallation(rows)
			if err != nil {
				rows.Close()
				return err
			}
			items = append(items, i)
		}
		err = rows.Err()
		rows.Close()
		if err != nil {
			return err
		}
		for _, i := range items {
			next, err := i.Disable(r.OccurredAt)
			if err != nil {
				return err
			}
			if err := saveInstallation(ctx, tx, next); err != nil {
				return err
			}
		}
		_, err = tx.Exec(ctx, `INSERT INTO notifications.installation_inbox(event_id,fingerprint) VALUES($1,$2)`, r.EventID.String(), hash)
		return err
	})
}
