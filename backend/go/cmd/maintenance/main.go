package main

import (
	"context"
	"flag"
	"fmt"
	"github.com/0xsj/mobile-foundry/backend/internal/identity"
	"github.com/0xsj/mobile-foundry/backend/migrations"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/env"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	storage "github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"os"
	"time"
)

func main() {
	limit := flag.Int("limit", 128, "Maximum expired identity challenges to remove (1..1000)")
	flag.Parse()
	if err := run(*limit); err != nil {
		v := apperrors.Public(err)
		fmt.Fprintln(os.Stderr, v.Kind, v.Code, v.Message)
		os.Exit(1)
	}
}
func run(limit int) error {
	if limit < 1 || limit > 1000 {
		return apperrors.New(apperrors.Invalid, "maintenance.limit", "invalid maintenance limit")
	}
	c, err := config.Load(env.Process)
	if err != nil {
		return err
	}
	if c.Storage != "postgres" || c.Authentication != "identity" {
		return storage.ErrConfiguration
	}
	schema, err := storage.Load(migrations.Files, identity.Schema)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	n, err := identity.PruneExpired(ctx, storage.Config{URL: c.IdentityDatabaseURL, MaxConns: c.PostgresPoolMax}, schema, time.Now(), limit)
	if err != nil {
		return err
	}
	fmt.Printf("Removed %d expired identity challenges\n", n)
	return nil
}
