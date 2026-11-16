# Mini Wallet

[![CI](https://github.com/khoaphamlee/mini-wallet/actions/workflows/ci.yml/badge.svg)](https://github.com/khoaphamlee/mini-wallet/actions/workflows/ci.yml)

An e-wallet backend that keeps every balance correct under concurrent requests, client retries and network failures. Users top up their wallet, send money to each other by phone number and review a clear transaction history.

It is a portfolio project built the way a production team would build it: requirements and design documents first, architecture decision records, migrations, automated tests and CI on every pull request.

> **Status:** in development. Project setup is in progress; the MVP (auth, top-up, transfer, history) is planned for January 2027.

## Features

**MVP**

- Register, log in and refresh tokens (JWT)
- Transaction PIN, locked for 15 minutes after 5 failed attempts
- Top up through a mock payment gateway with an HMAC-signed callback
- Transfer between wallets by phone number, with per-transaction and daily limits
- Transaction history with filters and pagination

**After MVP:** rate limiting, audit log, email notifications through RabbitMQ, nightly balance reconciliation, admin tools to lock and unlock wallets.

## Engineering highlights

| Problem | Approach |
| --- | --- |
| Two transfers drain the same wallet at the same time | Pessimistic row locks (`SELECT ... FOR UPDATE`) taken in ascending wallet id order to avoid deadlocks, plus `CHECK (balance >= 0)` in the database |
| A client retries a request after a timeout | `Idempotency-Key` header tracked in Redis, backed by a unique constraint in PostgreSQL |
| Proving where every balance change came from | Double-entry ledger: each successful transaction writes entries that sum to zero |
| Rounding errors in money | Amounts stored as `BIGINT` in VND, never floating point |
| Losing events between the database commit and the message broker | Transactional outbox (planned) |

## Tech stack

Java 21 · Spring Boot 4.1 · Spring Security · Spring Data JPA · PostgreSQL 17 · Flyway · Redis 7 · JUnit 5 · Testcontainers · Docker Compose · GitHub Actions

Frontend (planned): React and TypeScript.

## Getting started

Prerequisites: JDK 21 and Docker.

```bash
# 1. Start PostgreSQL and Redis
docker compose up -d

# 2. Run the backend with the default `local` profile
cd backend
./mvnw spring-boot:run      # Windows: mvnw spring-boot:run
```

Flyway applies the database migrations when the application starts.

### Running the tests

```bash
cd backend
./mvnw verify
```

Integration tests start their own PostgreSQL and Redis containers through Testcontainers, so Docker must be running. `verify` also checks code formatting; run `./mvnw spotless:apply` to fix it.

## Project structure

```
mini-wallet/
├── backend/              Spring Boot application
├── docs/                 Requirements, design documents and ADRs
├── docker-compose.yml    PostgreSQL and Redis for local development
└── .github/              CI workflow and pull request template
```

## Documentation

The design documents are written in Vietnamese.

- [Product requirements](docs/PRD.md)
- [Architecture](docs/architecture.md)
- [Database design](docs/database.md) and [ERD](docs/erd.png)
- [API specification (OpenAPI)](docs/openapi.yaml)
- [Architecture decision records](docs/adr/)

## Conventions

- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).
- `main` is protected: every change goes through a pull request and must pass CI.
- Java code is formatted with Spotless (Palantir Java Format).
