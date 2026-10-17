package main

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/account"
	"github.com/0xsj/mobile-foundry/backend/internal/audit"
	"github.com/0xsj/mobile-foundry/backend/internal/files"
	"github.com/0xsj/mobile-foundry/backend/internal/identity"
	"github.com/0xsj/mobile-foundry/backend/internal/notes"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications"
	"github.com/0xsj/mobile-foundry/backend/internal/realtime"
	"github.com/0xsj/mobile-foundry/backend/internal/server"
	"github.com/0xsj/mobile-foundry/backend/migrations"
	"github.com/0xsj/mobile-foundry/backend/pkg/broadcast"
	"github.com/0xsj/mobile-foundry/backend/pkg/capability"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/config"
	"github.com/0xsj/mobile-foundry/backend/pkg/env"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/logger"
	"github.com/0xsj/mobile-foundry/backend/pkg/module"
	"github.com/0xsj/mobile-foundry/backend/pkg/persistence/postgres"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	if err := run(ctx); err != nil {
		view := apperrors.Public(err)
		fmt.Fprintln(os.Stderr, view.Kind, view.Code, view.Message)
		os.Exit(1)
	}
}

func run(ctx context.Context) error {
	ctx, stopRuntime := context.WithCancel(ctx)
	defer stopRuntime()
	configuration, err := config.Load(env.Process)
	if err != nil {
		return err
	}
	log, err := logger.New(os.Stderr, configuration.LogFormat, configuration.LogLevel)
	if err != nil {
		return err
	}
	var principals principal.Resolver = principal.Deny{}
	if configuration.DevelopmentToken != "" {
		principals, err = principal.NewDevelopment(configuration.DevelopmentToken, configuration.DevelopmentPrincipal)
		if err != nil {
			return err
		}
		log.Warn("development credential fixture enabled")
	}
	registry := capability.NewRegistry()
	if configuration.Authentication == "identity" {
		principals = identity.CredentialResolver(registry.Client("api"))
	}
	signals := broadcast.New(256, 8)
	defer signals.Close()
	runtime := module.Runtime{Signals: signals, Clock: clock.System{}, IDs: id.Random{}, Capabilities: registry, Events: events.NewMemory(), Principals: principals}
	accountBuilder, auditBuilder := module.Builder(account.Build), module.Builder(audit.Build)
	mail, err := identity.LoadMailer(configuration.IdentityMailConfig, configuration.Environment != "production")
	if err != nil {
		return err
	}
	identityBuilder := identity.BuildWithMail(mail)
	notesBuilder := module.Builder(notes.Build)
	filesBuilder := module.Builder(files.Build)
	notificationsBuilder := module.Builder(notifications.Build)
	// Load only on explicit selection; no provider network traffic occurs at startup.
	pushGateway, closePush, err := notifications.LoadPushGateway(configuration.PushConfigFile, runtime.Clock)
	if err != nil {
		return err
	}
	defer closePush()
	notificationsBuilder = notifications.BuildWithPush(pushGateway, configuration.PushJobsEnabled)
	startup, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()
	if configuration.Storage == "postgres" {
		accountSchema, err := postgres.Load(migrations.Files, account.Schema)
		if err != nil {
			return err
		}
		auditSchema, err := postgres.Load(migrations.Files, audit.Schema)
		if err != nil {
			return err
		}
		accountBuilder = account.BuildPostgres(startup, postgres.Config{URL: configuration.AccountDatabaseURL, MaxConns: configuration.PostgresPoolMax}, accountSchema)
		auditBuilder = audit.BuildPostgres(startup, postgres.Config{URL: configuration.AuditDatabaseURL, MaxConns: configuration.PostgresPoolMax}, auditSchema)
		notesSchema, err := postgres.Load(migrations.Files, notes.Schema)
		if err != nil {
			return err
		}
		notesBuilder = notes.BuildPostgres(startup, postgres.Config{URL: configuration.NotesDatabaseURL, MaxConns: configuration.PostgresPoolMax}, notesSchema)
		filesSchema, err := postgres.Load(migrations.Files, files.Schema)
		if err != nil {
			return err
		}
		filesBuilder = files.BuildPostgres(startup, postgres.Config{URL: configuration.FilesDatabaseURL, MaxConns: configuration.PostgresPoolMax}, filesSchema, configuration.FilesDirectory)
		notificationsSchema, err := postgres.Load(migrations.Files, notifications.Schema)
		if err != nil {
			return err
		}
		notificationsBuilder = notifications.BuildPostgresWithPush(startup, postgres.Config{URL: configuration.NotificationsDatabaseURL, MaxConns: configuration.PostgresPoolMax}, notificationsSchema, pushGateway, configuration.PushJobsEnabled)
		if configuration.Authentication == "identity" {
			schema, err := postgres.Load(migrations.Files, identity.Schema)
			if err != nil {
				return err
			}
			identityBuilder = identity.BuildPostgresWithMail(startup, postgres.Config{URL: configuration.IdentityDatabaseURL, MaxConns: configuration.PostgresPoolMax}, schema, mail)
		}
	} else {
		log.Warn("memory storage enabled; data is lost on process exit")
	}
	builders := []module.Builder{accountBuilder, auditBuilder, notesBuilder, filesBuilder, notificationsBuilder, realtime.Build}
	if configuration.Authentication == "identity" {
		builders = append(builders, identityBuilder)
	}
	units, err := module.Assemble(runtime, builders...)
	if err != nil {
		return err
	}
	// The process is the API caller of this capability, so it owns the requirement.
	if configuration.Authentication == "identity" && !registry.Provides(identity.CredentialCapability) {
		return errors.Join(capability.ErrMissing, units.Close())
	}
	app, err := server.New(configuration, log, units)
	if err != nil {
		return errors.Join(err, units.Close())
	}
	var metrics *http.Server
	var metricsDone chan error
	if configuration.MetricsAddress != "" {
		listener, e := net.Listen("tcp", configuration.MetricsAddress)
		if e != nil {
			return errors.Join(e, units.Close())
		}
		metrics = &http.Server{Handler: app.Metrics(), ReadHeaderTimeout: 2 * time.Second, WriteTimeout: 3 * time.Second, IdleTimeout: 15 * time.Second, MaxHeaderBytes: 4096}
		metricsDone = make(chan error, 1)
		go func() {
			e := metrics.Serve(listener)
			if !errors.Is(e, http.ErrServerClosed) {
				stopRuntime()
			} else {
				e = nil
			}
			metricsDone <- e
		}()
		defer func() {
			budget, cancel := context.WithTimeout(context.Background(), 3*time.Second)
			defer cancel()
			_ = metrics.Shutdown(budget)
		}()
	}
	listener, err := net.Listen("tcp", configuration.Address)
	if err != nil {
		return errors.Join(err, units.Close())
	}
	log.Info("server listening", "address", listener.Addr().String(), "environment", configuration.Environment, "storage", configuration.Storage, "authentication", configuration.Authentication)
	serveErr := app.Serve(ctx, listener)
	if metrics != nil {
		_ = metrics.Close()
		serveErr = errors.Join(serveErr, <-metricsDone)
	}
	return serveErr
}
