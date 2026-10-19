.DEFAULT_GOAL := help
.PHONY: help dev check test parity-test
.PHONY: identity-parity-test
.PHONY: account-parity-test
.PHONY: audit-parity-test
.PHONY: postgres-parity-test migrate migrate-check
.PHONY: notes-parity-test
.PHONY: recovery-parity-test
.PHONY: files-parity-test
.PHONY: notifications-parity-test
.PHONY: installations-parity-test
.PHONY: push-parity-test
.PHONY: push-providers-parity-test
.PHONY: push-jobs-parity-test
.PHONY: realtime-parity-test
.PHONY: operations-test prod-config prod-build prod-db prod-migrate prod-up prod-down prod-maintenance prod-backup prod-restore

PROD_ENV_FILE ?= infra/production/.env.production
PROD_PROJECT ?= mobile-foundry-rust-prod
PROD_COMPOSE = docker compose --project-name $(PROD_PROJECT) --env-file $(PROD_ENV_FILE) -f infra/production/compose.yaml

operations-test:
	python3 -B scripts/operations_acceptance.py
prod-config:
	$(PROD_COMPOSE) config --quiet
prod-build:
	$(PROD_COMPOSE) build api
prod-db:
	$(PROD_COMPOSE) up -d --wait postgres
prod-migrate:
	$(PROD_COMPOSE) run --rm -T migrate
prod-up:
	$(PROD_COMPOSE) up -d --wait api edge
prod-down:
	$(PROD_COMPOSE) down
prod-maintenance:
	$(PROD_COMPOSE) run --rm -T maintenance --limit 128
prod-backup:
	@test -n "$(BACKUP_DIRECTORY)" || (echo "Set BACKUP_DIRECTORY"; exit 1)
	python3 -B scripts/backup.py create "$(BACKUP_DIRECTORY)" --project "$(PROD_PROJECT)" --env-file "$(PROD_ENV_FILE)"
prod-restore:
	@test -n "$(BACKUP_DIRECTORY)" -a -n "$(RESTORE_PROJECT)" || (echo "Set BACKUP_DIRECTORY and an unused RESTORE_PROJECT"; exit 1)
	python3 -B scripts/backup.py restore "$(BACKUP_DIRECTORY)" --project "$(RESTORE_PROJECT)" --env-file "$(PROD_ENV_FILE)"

realtime-parity-test:
	python3 -B ../../scripts/realtime_parity.py

push-jobs-parity-test:
	python3 -B ../../scripts/push_jobs_parity.py

push-providers-parity-test:
	python3 -B ../../scripts/push_providers_parity.py

push-parity-test:
	python3 -B ../../scripts/push_parity.py

installations-parity-test:
	python3 -B ../../scripts/installations_parity.py

notifications-parity-test:
	python3 -B ../../scripts/notifications_parity.py

files-parity-test:
	python3 -B ../../scripts/files_parity.py

recovery-parity-test:
	python3 -B ../../scripts/recovery_parity.py

notes-parity-test:
	python3 -B ../../scripts/notes_parity.py

postgres-parity-test:
	python3 -B ../../scripts/postgres_parity.py

migrate:
	cargo run --locked -p db-migrate

migrate-check:
	cargo run --locked -p db-migrate -- --check

audit-parity-test:
	python3 -B ../../scripts/audit_parity.py

account-parity-test:
	python3 -B ../../scripts/account_parity.py

identity-parity-test:
	python3 -B ../../scripts/identity_parity.py

help:
	@echo 'make operations-test Verify an owned Docker image, local edge/mail and backup/restore'
	@echo 'make prod-config Check the selected private portable deployment configuration'
	@echo 'make realtime-parity-test Verify Go/Rust live Notes hints and owned WebSockets'
	@echo 'make push-jobs-parity-test Verify Go/Rust retained jobs and owned recovery'
	@echo 'make push-providers-parity-test Verify local APNs/FCM protocols and guarded manual HTTP'
	@echo 'make push-parity-test Verify Go/Rust one-attempt dispatch and session eligibility'
	@echo 'make installations-parity-test Verify Go/Rust device registry and session retirement'
	@echo 'make notifications-parity-test Verify Go/Rust in-app delivery, inbox and preferences'
	@echo 'make files-parity-test Verify Go/Rust files, object bytes, receipts and Audit'
	@echo 'make recovery-parity-test Verify Go/Rust identity recovery and local SMTP'
	@echo 'make notes-parity-test Verify Go/Rust Notes receipts, CAS and scope'
	@echo 'make postgres-parity-test Verify disposable PostgreSQL transactions/restart and Go handoff'
	@echo 'make migrate     Apply selected owner migrations (requires STORAGE=postgres and URLs)'
	@echo 'make migrate-check Verify selected migration histories without applying SQL'
	@echo 'make audit-parity-test Verify memory event/Audit flows against Go'
	@echo 'make account-parity-test Verify memory Account/credential flows against Go'
	@echo 'make identity-parity-test Verify memory Identity against Go'
	@echo 'make dev         Run the Rust health/runtime API'
	@echo 'make check       Check formatting, Clippy and workspace build'
	@echo 'make test        Run workspace behavioral tests'
	@echo 'make parity-test Run shared runtime fixtures against Go and Rust'

dev:
	cargo run --locked -p api-server

check:
	cargo fmt --all -- --check
	cargo clippy --locked --workspace --all-targets -- -D warnings
	cargo build --locked --workspace

test:
	cargo test --locked --workspace

parity-test:
	python3 -B ../../scripts/backend_parity.py
