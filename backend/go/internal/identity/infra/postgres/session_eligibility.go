package postgres

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/jackc/pgx/v5"
	"time"
)

func (s *Store) SessionEligible(ctx context.Context, principal, session id.ID, now time.Time) (bool, error) {
	v, err := restoreSession(s.pool.QueryRow(ctx, `SELECT `+sessionColumns+` FROM identity.sessions WHERE id=$1 AND principal_id=$2`, session.String(), principal.String()))
	if errors.Is(err, pgx.ErrNoRows) {
		return false, nil
	}
	if err != nil {
		return false, storage.Failure(err)
	}
	return v.Renewable(now), nil
}
