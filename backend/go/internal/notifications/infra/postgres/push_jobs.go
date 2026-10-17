package postgres

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"time"
)

const pushJobColumns = `id::text,installation_id::text,principal_id::text,session_id::text,installation_version,status,attempts,claim::text,created_at,updated_at,expires_at,next_at,lease_until,code,retired,retry_of::text,duplicate_risk_accepted`

func restorePushJob(row pgx.Row) (domain.PushJob, error) {
	var v domain.PushJobSnapshot
	var identity, installation, principal, session string
	var claim *string
	var retry *string
	var lease *time.Time
	var version int64
	if err := row.Scan(&identity, &installation, &principal, &session, &version, &v.Status, &v.Attempts, &claim, &v.CreatedAt, &v.UpdatedAt, &v.ExpiresAt, &v.NextAt, &lease, &v.Code, &v.Retired, &retry, &v.DuplicateRiskAccepted); err != nil {
		return domain.PushJob{}, err
	}
	v.ID, v.InstallationID, v.PrincipalID, v.SessionID = id.ID(identity), id.ID(installation), id.ID(principal), id.ID(session)
	v.InstallationVersion = uint64(version)
	if claim != nil {
		v.Claim = id.ID(*claim)
	}
	if lease != nil {
		v.LeaseUntil = *lease
	}
	if retry != nil {
		v.RetryOf = id.ID(*retry)
	}
	return domain.RestorePushJob(v)
}
func (s *Store) FindPushJob(ctx context.Context, identity id.ID) (domain.PushJob, bool, error) {
	if !identity.Valid() {
		return domain.PushJob{}, false, app.ErrInput
	}
	job, err := restorePushJob(s.pool.QueryRow(ctx, `SELECT `+pushJobColumns+` FROM notifications.push_jobs WHERE id=$1`, identity.String()))
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.PushJob{}, false, nil
	}
	return job, err == nil, storage.Failure(err)
}
func (s *Store) QueuePushJob(ctx context.Context, job domain.PushJob) (domain.PushJob, error) {
	v := job.Snapshot()
	if _, err := domain.RestorePushJob(v); err != nil || v.Status != domain.JobQueued || v.Attempts != 0 || v.RetryOf != "" {
		return domain.PushJob{}, app.ErrInput
	}
	var result domain.PushJob
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `SELECT singleton FROM notifications.push_job_guard FOR UPDATE`); err != nil {
			return err
		}
		prior, err := restorePushJob(tx.QueryRow(ctx, `SELECT `+pushJobColumns+` FROM notifications.push_jobs WHERE id=$1`, v.ID.String()))
		if err == nil {
			if !prior.SameIntent(job) {
				return app.ErrJobReplay
			}
			result = prior
			return nil
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		err = insertPushJob(ctx, tx, job)
		result = job
		return err
	})
	return result, err
}
func savePushJob(ctx context.Context, tx pgx.Tx, job domain.PushJob) error {
	v := job.Snapshot()
	var claim, until any
	if v.Claim != "" {
		claim = v.Claim.String()
		until = v.LeaseUntil
	}
	_, err := tx.Exec(ctx, `UPDATE notifications.push_jobs SET status=$2,attempts=$3,claim=$4,updated_at=$5,next_at=$6,lease_until=$7,code=$8,retired=$9 WHERE id=$1`, v.ID.String(), v.Status, v.Attempts, claim, v.UpdatedAt, v.NextAt, until, v.Code, v.Retired)
	return err
}
func (s *Store) ClaimPushJob(ctx context.Context, claim id.ID, now time.Time) (domain.PushJob, bool, error) {
	if !claim.Valid() || now.IsZero() {
		return domain.PushJob{}, false, app.ErrInput
	}
	var result domain.PushJob
	var claimed bool
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		job, err := restorePushJob(tx.QueryRow(ctx, `SELECT `+pushJobColumns+` FROM notifications.push_jobs WHERE (status='queued' AND (next_at<=$1 OR expires_at<=$1)) OR (status='leased' AND (lease_until<=$1 OR expires_at<=$1)) OR (status='sending' AND lease_until<=$1) ORDER BY created_at,id FOR UPDATE SKIP LOCKED LIMIT 1`, now))
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		result, claimed, err = job.ClaimNext(claim, now)
		if err != nil {
			return err
		}
		return savePushJob(ctx, tx, result)
	})
	return result, claimed && err == nil, err
}
func (s *Store) updatePushJob(ctx context.Context, identity id.ID, change func(domain.PushJob) (domain.PushJob, error)) error {
	if !identity.Valid() {
		return app.ErrInput
	}
	return storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		prior, err := restorePushJob(tx.QueryRow(ctx, `SELECT `+pushJobColumns+` FROM notifications.push_jobs WHERE id=$1 FOR UPDATE`, identity.String()))
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.ErrJobClaim
		}
		if err != nil {
			return err
		}
		next, err := change(prior)
		if err != nil {
			return err
		}
		return savePushJob(ctx, tx, next)
	})
}
func (s *Store) BeginPushJob(ctx context.Context, identity, claim id.ID, now time.Time) error {
	return s.updatePushJob(ctx, identity, func(job domain.PushJob) (domain.PushJob, error) { return job.Begin(claim, now) })
}
func (s *Store) FinishPushJob(ctx context.Context, identity, claim id.ID, result app.PushJobResult, now time.Time) error {
	return s.updatePushJob(ctx, identity, func(job domain.PushJob) (domain.PushJob, error) {
		return job.Finish(claim, result.Started, result.Status, result.Code, result.Retired, now)
	})
}
