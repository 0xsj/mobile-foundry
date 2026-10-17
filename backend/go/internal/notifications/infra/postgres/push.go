package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
)

func (s *Store) PushTarget(ctx context.Context, lease app.PushLease, now time.Time) (app.PushAddress, error) {
	if err := lease.Validate(); err != nil {
		return app.PushAddress{}, err
	}
	var address app.PushAddress
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockInstallations(ctx, tx); err != nil {
			return err
		}
		i, err := restoreInstallation(tx.QueryRow(ctx, `SELECT `+installationColumns+` FROM notifications.installations WHERE id=$1`, lease.InstallationID.String()))
		if errors.Is(err, pgx.ErrNoRows) {
			return app.ErrPushTarget
		}
		if err != nil {
			return err
		}
		v := i.Snapshot()
		if !lease.Matches(v) || !v.Enabled || now.Before(v.CreatedAt) || !now.Before(v.ExpiresAt) {
			return app.ErrPushTarget
		}
		var revoked bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM notifications.revoked_installation_sessions WHERE principal_id=$1 AND session_id=$2)`, lease.PrincipalID.String(), lease.SessionID.String()).Scan(&revoked); err != nil {
			return err
		}
		if revoked {
			return app.ErrPushTarget
		}
		address = app.PushAddress{Lease: lease, Spec: v.Spec, RegisteredAt: v.UpdatedAt}
		return nil
	})
	return address, err
}

func (s *Store) RetirePushTarget(ctx context.Context, lease app.PushLease, now time.Time) (bool, error) {
	if err := lease.Validate(); err != nil {
		return false, err
	}
	retired := false
	err := storage.Transaction(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockInstallations(ctx, tx); err != nil {
			return err
		}
		i, err := restoreInstallation(tx.QueryRow(ctx, `SELECT `+installationColumns+` FROM notifications.installations WHERE id=$1`, lease.InstallationID.String()))
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		if !lease.Matches(i.Snapshot()) || !i.Snapshot().Enabled {
			return nil
		}
		next, err := i.Disable(now)
		if err != nil {
			return err
		}
		if err := saveInstallation(ctx, tx, next); err != nil {
			return err
		}
		retired = true
		return nil
	})
	return retired && err == nil, err
}
