.DEFAULT_GOAL := help
.PHONY: help dev build check test smoke migrate migrate-check db-up db-down db-check integration-test operations-test prod-config prod-build prod-db prod-migrate prod-up prod-down prod-maintenance prod-backup prod-restore
# Source copying never installs dependencies. The dev runner selects owned Identity.
export AUTH_MODE ?= identity
export POSTGRES_PORT ?= 58433
export DATABASE_URL ?= postgres://app:local-development-only@127.0.0.1:$(POSTGRES_PORT)/app?sslmode=disable

help:
	@echo 'make dev                Run the memory API with owned Identity'
	@echo 'make check test smoke   Check, test and exercise owned API processes'
	@echo 'make db-up migrate      Start retained local PostgreSQL and apply migrations'
	@echo 'make integration-test   Test only a newly owned disposable database'
	@echo 'make operations-test    Verify an owned image, local edge/mail and backup/restore'

dev:
	cargo run --locked -p api-server
build:
	cargo build --locked --workspace
check:
	cargo fmt --all -- --check
	cargo clippy --locked --workspace --all-targets -- -D warnings
	cargo build --locked --workspace
test:
	cargo test --locked --workspace
smoke:
	python3 -B scripts/smoke.py
migrate:
	@STORAGE=postgres cargo run --locked -p db-migrate
migrate-check:
	@STORAGE=postgres cargo run --locked -p db-migrate -- --check
db-up:
	docker compose -f infra/local/compose.yaml up -d --wait
db-down:
	docker compose -f infra/local/compose.yaml down
db-check:
	docker compose -f infra/local/compose.yaml config --quiet
integration-test:
	python3 -B scripts/postgres_integration.py

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
