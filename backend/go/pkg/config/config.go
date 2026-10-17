// Package config validates one startup snapshot. No reusable module reads os.Getenv.
package config

import (
	"net"
	"strconv"
	"strings"
	"time"

	"github.com/0xsj/mobile-foundry/backend/pkg/env"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type Runtime struct {
	PostgresPoolMax                               int32
	MetricsAddress                                string
	TrustedProxyCIDRs                             string
	Environment, Address, LogFormat, LogLevel     string
	ShutdownTimeout                               time.Duration
	DevelopmentToken                              string
	DevelopmentPrincipal                          id.ID
	Storage, AccountDatabaseURL, AuditDatabaseURL string
	Authentication, IdentityDatabaseURL           string
	NotesDatabaseURL                              string
	FilesDatabaseURL, FilesDirectory              string
	NotificationsDatabaseURL                      string
	IdentityMailConfig                            string
	PushConfigFile                                string
	PushJobsEnabled                               bool
}

// Runtime may contain credentials; formatting it never prints the snapshot.
func (Runtime) String() string   { return "config.Runtime{redacted}" }
func (Runtime) GoString() string { return "config.Runtime{redacted}" }

func Load(source env.Source) (Runtime, error) {
	if source == nil {
		return Runtime{}, invalid("environment source is required")
	}
	configuration := Runtime{
		Environment: source.String("APP_ENV", "development"), Address: source.String("HTTP_ADDR", "127.0.0.1:8080"),
		LogFormat: source.String("LOG_FORMAT", "console"), LogLevel: source.String("LOG_LEVEL", "info"),
		ShutdownTimeout: 10 * time.Second,
	}
	poolMax, err := strconv.Atoi(source.String("POSTGRES_POOL_MAX_CONNS", "8"))
	if err != nil || poolMax < 1 || poolMax > 128 {
		return Runtime{}, invalid("POSTGRES_POOL_MAX_CONNS must be 1..128")
	}
	configuration.PostgresPoolMax = int32(poolMax)
	configuration.MetricsAddress = source.String("METRICS_ADDR", "")
	if configuration.MetricsAddress != "" {
		if _, _, err := net.SplitHostPort(configuration.MetricsAddress); err != nil {
			return Runtime{}, invalid("invalid METRICS_ADDR")
		}
	}
	configuration.TrustedProxyCIDRs = source.String("TRUSTED_PROXY_CIDRS", "")
	if configuration.TrustedProxyCIDRs != "" {
		for _, value := range strings.Split(configuration.TrustedProxyCIDRs, ",") {
			if _, _, err := net.ParseCIDR(value); err != nil {
				return Runtime{}, invalid("invalid TRUSTED_PROXY_CIDRS")
			}
		}
	}
	configuration.Storage = source.String("STORAGE", "memory")
	configuration.Authentication = source.String("AUTH_MODE", "deny")
	switch configuration.Authentication {
	case "deny", "development", "identity":
	default:
		return Runtime{}, invalid("AUTH_MODE must be deny, development, or identity")
	}
	switch configuration.Storage {
	case "memory":
	case "postgres":
		common := source.String("DATABASE_URL", "")
		configuration.AccountDatabaseURL = source.String("ACCOUNT_DATABASE_URL", common)
		configuration.AuditDatabaseURL = source.String("AUDIT_DATABASE_URL", common)
		configuration.IdentityDatabaseURL = source.String("IDENTITY_DATABASE_URL", common)
		configuration.NotesDatabaseURL = source.String("NOTES_DATABASE_URL", common)
		configuration.FilesDatabaseURL = source.String("FILES_DATABASE_URL", common)
		configuration.NotificationsDatabaseURL = source.String("NOTIFICATIONS_DATABASE_URL", common)
		if configuration.NotificationsDatabaseURL == "" {
			return Runtime{}, invalid("postgres storage requires NOTIFICATIONS_DATABASE_URL or DATABASE_URL")
		}
		configuration.FilesDirectory = source.String("FILES_DIRECTORY", ".data/files")
		if configuration.FilesDatabaseURL == "" || configuration.FilesDirectory == "" {
			return Runtime{}, invalid("postgres files require FILES_DATABASE_URL or DATABASE_URL and a nonempty FILES_DIRECTORY")
		}
		if configuration.NotesDatabaseURL == "" {
			return Runtime{}, invalid("postgres storage requires NOTES_DATABASE_URL or DATABASE_URL")
		}
		if configuration.AccountDatabaseURL == "" || configuration.AuditDatabaseURL == "" {
			return Runtime{}, invalid("postgres storage requires DATABASE_URL or the selected module database URLs")
		}
		if configuration.Authentication == "identity" && configuration.IdentityDatabaseURL == "" {
			return Runtime{}, invalid("identity authentication requires IDENTITY_DATABASE_URL or DATABASE_URL")
		}
	default:
		return Runtime{}, invalid("STORAGE must be memory or postgres")
	}
	switch configuration.Environment {
	case "development", "test", "production":
	default:
		return Runtime{}, invalid("APP_ENV must be development, test, or production")
	}
	switch configuration.LogFormat {
	case "console", "json":
	default:
		return Runtime{}, invalid("LOG_FORMAT must be console or json")
	}
	switch configuration.LogLevel {
	case "debug", "info", "warn", "error":
	default:
		return Runtime{}, invalid("LOG_LEVEL must be debug, info, warn, or error")
	}
	_, port, err := net.SplitHostPort(configuration.Address)
	if err != nil {
		return Runtime{}, invalid("HTTP_ADDR must be host:port")
	}
	parsedPort, err := strconv.Atoi(port)
	if err != nil || parsedPort < 1 || parsedPort > 65535 {
		return Runtime{}, invalid("HTTP_ADDR port must be 1 through 65535")
	}
	token, principalID := source.String("DEV_AUTH_TOKEN", ""), source.String("DEV_PRINCIPAL_ID", "")
	if token != "" || principalID != "" {
		if mode, explicit := source("AUTH_MODE"); explicit && mode != "development" {
			return Runtime{}, invalid("development credentials cannot be combined with this AUTH_MODE")
		}
		if configuration.Environment == "production" {
			return Runtime{}, invalid("development credentials are forbidden in production")
		}
		if len(token) < 8 || len(token) > 128 || strings.ContainsAny(token, " \t\r\n") {
			return Runtime{}, invalid("DEV_AUTH_TOKEN must be 8 to 128 non-whitespace characters")
		}
		identity, err := id.Parse(principalID)
		if err != nil {
			return Runtime{}, invalid("DEV_PRINCIPAL_ID must be a canonical UUID paired with DEV_AUTH_TOKEN")
		}
		host, _, _ := net.SplitHostPort(configuration.Address)
		if host != "localhost" && !net.ParseIP(host).IsLoopback() {
			return Runtime{}, invalid("development credentials require a loopback HTTP_ADDR")
		}
		configuration.DevelopmentToken, configuration.DevelopmentPrincipal = token, identity
		configuration.Authentication = "development"
	}
	if configuration.Authentication == "development" && configuration.DevelopmentToken == "" {
		return Runtime{}, invalid("development authentication requires paired fixture credentials")
	}
	configuration.IdentityMailConfig = source.String("IDENTITY_MAIL_CONFIG", "")
	if configuration.IdentityMailConfig != "" && configuration.Authentication != "identity" {
		return Runtime{}, invalid("IDENTITY_MAIL_CONFIG requires owned Identity")
	}
	configuration.PushConfigFile = source.String("PUSH_CONFIG_FILE", "")
	queued := source.String("PUSH_JOBS_ENABLED", "false")
	if queued != "true" && queued != "false" {
		return Runtime{}, invalid("PUSH_JOBS_ENABLED must be true or false")
	}
	configuration.PushJobsEnabled = queued == "true"
	if configuration.PushJobsEnabled && (configuration.PushConfigFile == "" || configuration.Authentication != "identity" || configuration.Storage != "postgres") {
		return Runtime{}, invalid("PUSH_JOBS_ENABLED requires private push configuration, owned Identity and postgres storage")
	}
	if configuration.PushConfigFile != "" && (configuration.Authentication != "identity" || configuration.PushConfigFile != strings.TrimSpace(configuration.PushConfigFile)) {
		return Runtime{}, invalid("PUSH_CONFIG_FILE requires AUTH_MODE=identity and a nonblank private configuration path")
	}
	return configuration, nil
}

func invalid(message string) error {
	return apperrors.New(apperrors.Invalid, "config.invalid", message).WithPublic(message)
}
