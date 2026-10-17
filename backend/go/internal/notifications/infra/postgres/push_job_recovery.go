package postgres

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"time"
)

// Caller holds push_job_guard; capacity and immutable insertion share one transaction.
func insertPushJob(ctx context.Context, tx pgx.Tx, job domain.PushJob) error {
	var count int
	if err := tx.QueryRow(ctx, `SELECT count(*) FROM notifications.push_jobs`).Scan(&count); err != nil {
		return err
	}
	if count >= 10000 {
		return app.ErrCapacity
	}
	v := job.Snapshot()
	var retry any
	if v.RetryOf != "" {
		retry = v.RetryOf.String()
	}
	_, err := tx.Exec(ctx, `INSERT INTO notifications.push_jobs(id,installation_id,principal_id,session_id,installation_version,status,attempts,created_at,updated_at,expires_at,next_at,code,retired,retry_of,duplicate_risk_accepted) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)`, v.ID.String(), v.InstallationID.String(), v.PrincipalID.String(), v.SessionID.String(), int64(v.InstallationVersion), v.Status, v.Attempts, v.CreatedAt, v.UpdatedAt, v.ExpiresAt, v.NextAt, v.Code, v.Retired, retry, v.DuplicateRiskAccepted)
	return err
}
func (s *Store) CancelPushJob(ctx context.Context, owner app.PushJobOwner, now time.Time) (domain.PushJob, error) {
	if err := owner.Validate(); err != nil {
		return domain.PushJob{}, err
	}
	var result domain.PushJob
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		prior, err := restorePushJob(tx.QueryRow(ctx, `SELECT `+pushJobColumns+` FROM notifications.push_jobs WHERE id=$1 FOR UPDATE`, owner.ID.String()))
		if errors.Is(err, pgx.ErrNoRows) {
			return app.ErrMissing
		}
		if err != nil {
			return err
		}
		if !owner.Matches(prior) {
			return app.ErrMissing
		}
		result, err = prior.Cancel(now)
		if err != nil {
			return err
		}
		if prior.Snapshot().Status == domain.JobCanceled {
			return nil
		}
		return savePushJob(ctx, tx, result)
	})
	return result, err
}
func (s *Store) ReplacePushJob(ctx context.Context, candidate domain.PushJob) (domain.PushJob, error) {
	v := candidate.Snapshot()
	if _, err := domain.RestorePushJob(v); err != nil || !v.RetryOf.Valid() || v.Status != domain.JobQueued || v.Attempts != 0 {
		return domain.PushJob{}, app.ErrInput
	}
	var result domain.PushJob
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `SELECT singleton FROM notifications.push_job_guard FOR UPDATE`); err != nil {
			return err
		}
		prior, err := restorePushJob(tx.QueryRow(ctx, `SELECT `+pushJobColumns+` FROM notifications.push_jobs WHERE id=$1`, v.ID.String()))
		if err == nil {
			if !prior.SameIntent(candidate) {
				return app.ErrJobReplay
			}
			result = prior
			return nil
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		parent, err := restorePushJob(tx.QueryRow(ctx, `SELECT `+pushJobColumns+` FROM notifications.push_jobs WHERE id=$1 FOR UPDATE`, v.RetryOf.String()))
		if errors.Is(err, pgx.ErrNoRows) {
			return app.ErrMissing
		}
		if err != nil {
			return err
		}
		owner := app.PushJobOwner{ID: v.RetryOf, InstallationID: v.InstallationID, PrincipalID: v.PrincipalID, SessionID: v.SessionID}
		if !owner.Matches(parent) {
			return app.ErrMissing
		}
		result, err = parent.Replacement(candidate)
		if err != nil {
			return err
		}
		var exists bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM notifications.push_jobs WHERE retry_of=$1)`, v.RetryOf.String()).Scan(&exists); err != nil {
			return err
		}
		if exists {
			return app.ErrJobReplacement
		}
		return insertPushJob(ctx, tx, result)
	})
	return result, err
}
func (s *Store) FindPushJobReplacement(ctx context.Context, owner app.PushJobOwner) (domain.PushJob, bool, error) {
	if err := owner.Validate(); err != nil {
		return domain.PushJob{}, false, err
	}
	job, err := restorePushJob(s.pool.QueryRow(ctx, `SELECT `+pushJobColumns+` FROM notifications.push_jobs WHERE retry_of=$1 AND installation_id=$2 AND principal_id=$3 AND session_id=$4`, owner.ID.String(), owner.InstallationID.String(), owner.PrincipalID.String(), owner.SessionID.String()))
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.PushJob{}, false, nil
	}
	return job, err == nil, storage.Failure(err)
}
