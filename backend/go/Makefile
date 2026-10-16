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
