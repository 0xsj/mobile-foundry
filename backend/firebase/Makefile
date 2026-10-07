.DEFAULT_GOAL := help
.PHONY: help check up down logs status

help:
	@echo 'make check   Validate Compose configuration'
	@echo 'make up      Build and start Auth, Firestore, Storage, and Emulator UI'
	@echo 'make down    Stop gracefully and retain exported emulator data'
	@echo 'make status  Show container status'

check:
	docker compose config --quiet

up: check
	docker compose up --build -d --wait --wait-timeout 180

down:
	docker compose down

logs:
	docker compose logs -f

status:
	docker compose ps
