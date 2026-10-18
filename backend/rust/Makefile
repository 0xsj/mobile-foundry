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
