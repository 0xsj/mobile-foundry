package identity

import (
	"context"
	adapter "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"time"
)

// PruneExpired removes only expired single-use challenges, never credentials,
// revocation history, audit evidence or command receipts.
func PruneExpired(ctx context.Context, config storage.Config, schema storage.Schema, now time.Time, limit int) (int, error) {
	pool, err := storage.Open(ctx, config)
	if err != nil {
		return 0, err
	}
	defer pool.Close()
	if err = schema.Check(ctx, pool); err != nil {
		return 0, err
	}
	store, err := adapter.New(pool, id.Random{})
	if err != nil {
		return 0, err
	}
	return store.PruneChallenges(ctx, now, limit)
}
