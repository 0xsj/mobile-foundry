package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/0xsj/mobile-foundry/backend/migrations"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/env"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
)

func main() {
	check := flag.Bool("check", false, "verify migrations without applying them")
	flag.Parse()
	if err := run(*check); err != nil {
		view := apperrors.Public(err)
		fmt.Fprintln(os.Stderr, view.Kind, view.Code, view.Message)
		os.Exit(1)
	}
}
func run(check bool) error {
	configuration, err := config.Load(env.Process)
	if err != nil {
		return err
	}
	if configuration.Storage != "postgres" {
		return postgres.ErrConfiguration
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	owners := []struct{ name, url string }{{"account", configuration.AccountDatabaseURL}, {"audit", configuration.AuditDatabaseURL}, {"notes", configuration.NotesDatabaseURL}, {"files", configuration.FilesDatabaseURL}}
	if configuration.IdentityDatabaseURL != "" {
		owners = append(owners, struct{ name, url string }{"identity", configuration.IdentityDatabaseURL})
	}
	for _, owner := range owners {
		schema, err := postgres.Load(migrations.Files, owner.name)
		if err != nil {
			return err
		}
		pool, err := postgres.Open(ctx, postgres.Config{URL: owner.url})
		if err != nil {
			return err
		}
		if check {
			err = schema.Check(ctx, pool)
		} else {
			err = schema.Apply(ctx, pool)
		}
		pool.Close()
		if err != nil {
			return err
		}
		fmt.Println(owner.name + " migrations verified")
	}
	return nil
}
