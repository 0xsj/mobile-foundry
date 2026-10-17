package config

import (
	"testing"

	"github.com/0xsj/mobile-foundry/backend/pkg/env"
)

func source(values map[string]string) env.Source {
	return func(key string) (string, bool) { value, found := values[key]; return value, found }
}

func TestPushJobsRequireExplicitDurableProviderSelection(t *testing.T) {
	for _, values := range []map[string]string{
		{"PUSH_JOBS_ENABLED": "yes"}, {"PUSH_JOBS_ENABLED": "true"},
		{"PUSH_JOBS_ENABLED": "true", "PUSH_CONFIG_FILE": "private.json", "AUTH_MODE": "identity"},
		{"PUSH_JOBS_ENABLED": "true", "AUTH_MODE": "identity", "STORAGE": "postgres", "DATABASE_URL": "common"},
	} {
		if _, err := Load(source(values)); err == nil {
			t.Fatal("unsafe queued setup admitted")
		}
	}
	valid, err := Load(source(map[string]string{"PUSH_JOBS_ENABLED": "true", "PUSH_CONFIG_FILE": "private.json", "AUTH_MODE": "identity", "STORAGE": "postgres", "DATABASE_URL": "common"}))
	if err != nil || !valid.PushJobsEnabled {
		t.Fatal("durable queued selection refused", err)
	}
	off, err := Load(source(nil))
	if err != nil || off.PushJobsEnabled {
		t.Fatal("jobs enabled by default", err)
	}
}

func TestDevelopmentCredentialsRequireExplicitSafeComposition(t *testing.T) {
	identity := "00000000-0000-4000-8000-000000000001"
	for _, values := range []map[string]string{
		{"DEV_AUTH_TOKEN": "fixture-token"}, {"DEV_PRINCIPAL_ID": identity},
		{"DEV_AUTH_TOKEN": "short", "DEV_PRINCIPAL_ID": identity},
		{"DEV_AUTH_TOKEN": "fixture-token", "DEV_PRINCIPAL_ID": identity, "APP_ENV": "production"},
		{"DEV_AUTH_TOKEN": "fixture-token", "DEV_PRINCIPAL_ID": identity, "HTTP_ADDR": ":8080"},
	} {
		if _, err := Load(source(values)); err == nil {
			t.Fatal("unsafe development configuration admitted", values)
		}
	}
	configured, err := Load(source(map[string]string{"DEV_AUTH_TOKEN": "fixture-token", "DEV_PRINCIPAL_ID": identity, "HTTP_ADDR": "[::1]:8080"}))
	if err != nil || configured.DevelopmentPrincipal.String() != identity {
		t.Fatal("valid fixture refused", err)
	}
}
func TestDefaultsAndAdmission(t *testing.T) {
	defaults, err := Load(source(nil))
	if err != nil || defaults.Address != "127.0.0.1:8080" || defaults.Environment != "development" {
		t.Fatalf("bad defaults: %+v %v", defaults, err)
	}
	for _, values := range []map[string]string{{"APP_ENV": "dev"}, {"HTTP_ADDR": ":0"}, {"HTTP_ADDR": "localhost:65536"}, {"HTTP_ADDR": "localhost"}, {"LOG_LEVEL": "verbose"}, {"LOG_FORMAT": "yaml"}, {"APP_ENV": ""}} {
		if _, err := Load(source(values)); err == nil {
			t.Fatalf("admitted %+v", values)
		}
	}
	if _, err := Load(nil); err == nil {
		t.Fatal("nil environment accepted")
	}
	configured, err := Load(source(map[string]string{"APP_ENV": "production", "HTTP_ADDR": "[::1]:8081", "LOG_FORMAT": "json", "LOG_LEVEL": "warn"}))
	if err != nil || configured.Address != "[::1]:8081" {
		t.Fatalf("valid override rejected: %v", err)
	}
}

func TestPushConfigurationIsExplicitAndRequiresOwnedIdentity(t *testing.T) {
	for _, values := range []map[string]string{{"PUSH_CONFIG_FILE": "private.json"}, {"PUSH_CONFIG_FILE": " "}, {"AUTH_MODE": "identity", "PUSH_CONFIG_FILE": " private.json"}} {
		if _, err := Load(source(values)); err == nil {
			t.Fatal("unsafe provider selection")
		}
	}
	configured, err := Load(source(map[string]string{"AUTH_MODE": "identity", "PUSH_CONFIG_FILE": ".push-private/push.local.json"}))
	if err != nil || configured.PushConfigFile != ".push-private/push.local.json" {
		t.Fatal("valid explicit provider selection", err)
	}
}

func TestStorageSelectionAndModuleOverrides(t *testing.T) {
	for _, values := range []map[string]string{{"STORAGE": "sqlite"}, {"STORAGE": "postgres"}, {"STORAGE": "postgres", "ACCOUNT_DATABASE_URL": "account-url"}} {
		if _, err := Load(source(values)); err == nil {
			t.Fatal("invalid storage accepted")
		}
	}
	configured, err := Load(source(map[string]string{"STORAGE": "postgres", "DATABASE_URL": "common", "AUDIT_DATABASE_URL": "audit"}))
	if err != nil || configured.AccountDatabaseURL != "common" || configured.AuditDatabaseURL != "audit" || configured.NotesDatabaseURL != "common" || configured.NotificationsDatabaseURL != "common" {
		t.Fatal("module override lost", err)
	}
	configured, err = Load(source(map[string]string{"STORAGE": "postgres", "ACCOUNT_DATABASE_URL": "a", "AUDIT_DATABASE_URL": "b", "NOTES_DATABASE_URL": "n", "FILES_DATABASE_URL": "f", "NOTIFICATIONS_DATABASE_URL": "notify"}))
	if err != nil || configured.NotesDatabaseURL != "n" || configured.NotificationsDatabaseURL != "notify" {
		t.Fatal("owned module override refused", err)
	}
}
func TestAuthenticationSelection(t *testing.T) {
	for _, values := range []map[string]string{{"AUTH_MODE": "unknown"}, {"AUTH_MODE": "development"}, {"AUTH_MODE": "identity", "DEV_AUTH_TOKEN": "fixture-token", "DEV_PRINCIPAL_ID": "00000000-0000-4000-8000-000000000001"}, {"AUTH_MODE": "identity", "STORAGE": "postgres", "ACCOUNT_DATABASE_URL": "a", "AUDIT_DATABASE_URL": "b"}} {
		if _, err := Load(source(values)); err == nil {
			t.Fatal("invalid auth selection admitted")
		}
	}
	c, err := Load(source(map[string]string{"AUTH_MODE": "identity", "STORAGE": "postgres", "DATABASE_URL": "common", "IDENTITY_DATABASE_URL": "own"}))
	if err != nil || c.IdentityDatabaseURL != "own" || c.Authentication != "identity" {
		t.Fatal(err)
	}
}
