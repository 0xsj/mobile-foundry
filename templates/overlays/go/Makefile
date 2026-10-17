.DEFAULT_GOAL := help
.PHONY: help dev build fmt check architecture-check test smoke db-up db-down db-check migrate migrate-check integration-test
# The development runner explicitly selects owned Identity; the binary still defaults to deny.
export AUTH_MODE ?= identity
export POSTGRES_PORT ?= 58432
export DATABASE_URL ?= postgres://app:local-development-only@127.0.0.1:$(POSTGRES_PORT)/app?sslmode=disable

help:
	@echo 'make dev                 Run the memory API with owned Identity'
	@echo 'make check test smoke    Check, race-test and exercise temporary API processes'
	@echo 'make db-up migrate       Start retained local PostgreSQL and apply migrations'
	@echo 'STORAGE=postgres make dev Run the durable API'
	@echo 'make integration-test    Own and test a disposable PostgreSQL database'

dev:
	go run ./cmd/server

build:
	go build ./...

fmt:
	gofmt -w cmd internal pkg tests migrations

architecture-check:
	go test ./internal/architecture

check:
	@test -z "$$(gofmt -l cmd internal pkg tests migrations)" || (gofmt -l cmd internal pkg tests migrations; exit 1)
	go test ./internal/architecture
	go vet ./...
	go build ./...

test:
	go test -race ./...

smoke:
	python3 -B scripts/smoke.py

db-up:
	docker compose -f infra/local/compose.yaml up -d --wait

db-down:
	docker compose -f infra/local/compose.yaml down

db-check:
	docker compose -f infra/local/compose.yaml config --quiet

migrate:
	@STORAGE=postgres go run ./cmd/migrate

migrate-check:
	@STORAGE=postgres go run ./cmd/migrate --check

integration-test:
	python3 -B scripts/postgres_integration.py

PROD_ENV_FILE ?= infra/production/.env.production
PROD_PROJECT ?= mobile-foundry-prod
PROD_COMPOSE = docker compose --project-name $(PROD_PROJECT) --env-file $(PROD_ENV_FILE) -f infra/production/compose.yaml
.PHONY: operations-test prod-config prod-build prod-db prod-migrate prod-up prod-down prod-maintenance prod-backup prod-restore
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
