package config

import (
	"testing"

	"github.com/0xsj/mobile-foundry/backend/pkg/env"
)

func source(values map[string]string) env.Source {
	return func(key string) (string, bool) { value, found := values[key]; return value, found }
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

func TestStorageSelectionAndModuleOverrides(t *testing.T) {
	for _, values := range []map[string]string{{"STORAGE": "sqlite"}, {"STORAGE": "postgres"}, {"STORAGE": "postgres", "ACCOUNT_DATABASE_URL": "account-url"}} {
		if _, err := Load(source(values)); err == nil {
			t.Fatal("invalid storage accepted")
		}
	}
	configured, err := Load(source(map[string]string{"STORAGE": "postgres", "DATABASE_URL": "common", "AUDIT_DATABASE_URL": "audit"}))
	if err != nil || configured.AccountDatabaseURL != "common" || configured.AuditDatabaseURL != "audit" || configured.NotesDatabaseURL != "common" {
		t.Fatal("module override lost", err)
	}
	configured, err = Load(source(map[string]string{"STORAGE": "postgres", "ACCOUNT_DATABASE_URL": "a", "AUDIT_DATABASE_URL": "b", "NOTES_DATABASE_URL": "n"}))
	if err != nil || configured.NotesDatabaseURL != "n" {
		t.Fatal("owned Notes override refused", err)
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
