.DEFAULT_GOAL := help
.PHONY: help dev build fmt check architecture-check test smoke db-up db-down db-check migrate migrate-check integration-test
export DATABASE_URL ?= postgres://mobile_foundry:local-development-only@127.0.0.1:58432/mobile_foundry?sslmode=disable

help:
	@echo 'make dev                 Run the local API (no external services)'
	@echo 'AUTH_MODE=identity make dev Select owned password/session authentication'
	@echo 'make check               Check formatting, imports, vet, and build'
	@echo 'make test                Run all tests with the race detector'
	@echo 'make smoke               Build and test temporary loopback API processes'
	@echo 'make db-up migrate       Start local PostgreSQL and apply owned migrations'
	@echo 'STORAGE=postgres make dev Run with durable stores (fixture credentials opt-in)'
	@echo 'make integration-test    Test an isolated disposable PostgreSQL container'

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
