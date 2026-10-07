.DEFAULT_GOAL := help
.PHONY: help setup check up down logs status

help:
	@echo 'make setup   Generate local .env once (requires OpenSSL and Node.js)'
	@echo 'make check   Validate Compose configuration'
	@echo 'make up      Start local Supabase and email capture'
	@echo 'make down    Stop containers; retain data volumes'
	@echo 'make status  Show container status'

setup:
	sh bootstrap.sh

check: setup
	docker compose config --quiet

up: check
	docker compose up -d --wait --wait-timeout 240

down:
	docker compose down

logs:
	docker compose logs -f

status:
	docker compose ps
