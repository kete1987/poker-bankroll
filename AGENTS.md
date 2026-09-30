# AGENTS.md

Context and rules for AI coding agents (and humans) working on this repository.
`CLAUDE.md` only imports this file: keep everything here.

## Project

**poker-bankroll** is a self-hosted web app to manage a poker bankroll: record results of
tournaments, Sit&Go, Expresso (spin/lottery SNG) and cash games, track the real balance of
each poker room, and see statistics over time.

- Single user, **no authentication**. Meant to run on a home server / LAN (Docker Compose, Portainer).
- Focus is results and bankroll, **not** hand or tournament analysis (finishing position,
  field size, hand histories are out of scope — tools like PokerTracker 4 cover that).
- Open source (MIT). Code, commits, issues and docs are in **English**; the UI ships in
  English and Spanish.

Roadmap and tasks: GitHub issues, milestones (`v0.1 MVP`, `v0.2`, `Backlog`) and the project board.
Issue titles carry an ID (`[INF-1]`, `[API-3]`, `[UI-2]`...) used across discussions.

## Repository layout

| Path | Content |
|---|---|
| `backend/` | REST API — Java 25, Spring Boot 4.1, Flyway, springdoc-openapi |
| `frontend/` | Web app — React, Vite, TypeScript, React Router, TanStack Query, Mantine, react-i18next, ECharts |
| `deploy/` | `docker-compose.yml` (db + api + web + backup), `nginx.conf`, `.env.example` |
| `docs/` | User and developer documentation |
| `.github/workflows/` | CI (tests per PR) and release (multi-arch images to GHCR on tag) |

Runtime architecture: `web` (nginx serving the SPA and proxying `/api`) → `api` (Spring Boot)
→ `db` (PostgreSQL 18). Only `web` exposes a port. Both images share the same version tag.

## Commands

> Update this section in the same PR that introduces or changes a command.

### Backend (`backend/`)

Requires JDK 25 (`JAVA_HOME`) and a running Docker daemon (tests use Testcontainers).
Use the Maven wrapper; on Windows use `mvnw.cmd` instead of `./mvnw`.

| Command | What it does |
|---|---|
| `./mvnw verify` | Compile and run all tests (starts a PostgreSQL container) |
| `./mvnw test -Dtest=ClassName` | Run a single test class |
| `./mvnw spring-boot:test-run` | Run the API on `:8080` against a throwaway PostgreSQL container |
| `./mvnw spring-boot:run` | Run the API against the PostgreSQL configured in `application.yaml` / env vars |

With the API running: health at `http://localhost:8080/api/actuator/health`, Swagger UI at
`http://localhost:8080/api/swagger-ui.html`, OpenAPI spec at `http://localhost:8080/api/v3/api-docs`.

## Domain glossary

- **Room**: poker site (Winamax, 888poker...). Has a default currency.
- **Game type**: `TOURNAMENT`, `SIT_AND_GO`, `EXPRESSO`, `CASH`.
- **Variant**: sub-type within a game type (`NORMAL`, `NITRO`, `DOUBLE_OR_NOTHING`, `KO`...).
  Known variants are codes translated by the frontend; users can add custom ones.
- **Game**: one recorded result. Date only (`played_on`), no time of day.
  - `buy_in`: price of one entry (cash game: initial amount brought to the table).
  - `entries`: number of entries including re-entries (default 1).
  - `prize`: cash won (cash game: final amount when leaving the table).
  - `bounty`: bounties/KO money won.
  - `ticket_prize_value`: value of a tournament ticket won (satellites).
  - `paid_with_ticket`: one entry was paid with a ticket instead of cash.
  - `multiplier`: Expresso prize-pool multiplier.
  - **net** = `prize + bounty + ticket_prize_value − buy_in × entries`.
- **ITM** (in the money): `prize > 0` or `ticket_prize_value > 0`. "With prize" also counts bounties.
- **ROI** = net / total invested.
- **Bankroll movement**: `DEPOSIT`, `WITHDRAWAL`, `BONUS`, `ADJUSTMENT` (reconciles with the
  real balance shown by the room).
- **Cash effect of a game** = `prize + bounty − buy_in × (entries − (paid_with_ticket ? 1 : 0))`.
  Ticket prizes do not add cash; they are tracked as pending tickets per room.
- **Room balance** = sum of movements + cash effect of its games.

## Conventions

### Money and currency
- Money is `BigDecimal` in Java and `NUMERIC(12,2)` in PostgreSQL. **Never** `float`/`double`.
- Every amount travels with its currency (ISO 4217 code). **Never sum amounts in different
  currencies**; aggregate per currency or convert explicitly (F-1).
- All aggregation happens in the backend; the frontend only formats values (`Intl.NumberFormat`).
- Currencies live in the `currency` table; adding one must not require code changes.

### API
- REST, JSON, ISO-8601 dates (`yyyy-MM-dd`).
- The servlet context path is `/api` (`server.servlet.context-path`), so controllers map
  `/games`, not `/api/games`. Actuator and OpenAPI live under `/api` too.
- Responses contain **codes, not translated text** (enums, variant codes, error codes).
- Errors are RFC 9457 `application/problem+json` built by `GlobalExceptionHandler`, with a
  `code` property (`ErrorCode` enum) and, for validation errors, an `errors` list of
  `{field, code, message}` (`field` is `null` for object-level constraints).
  Business errors throw `ApiException(ErrorCode, args...)`.
  The `detail` is resolved from `messages.properties` (English, default) /
  `messages_es.properties` using `Accept-Language`; add every new key to both files.
- The OpenAPI spec is the contract; frontend types are generated from it (API-7).

### Backend code
- Base package `io.github.kete1987.pokerbankroll`, organised **by feature**
  (`game`, `room`, `bankroll`, `stats`...), with cross-cutting code in `common`.
- Configuration comes from `application.yaml`; override it with standard Spring environment
  variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`...).
- Hibernate never changes the schema (`ddl-auto: validate`); Flyway owns it.
- Validate input with Bean Validation on request DTOs (`@Valid @RequestBody`) and on simple
  parameters. Rules spanning several fields go in a **class-level constraint on the DTO**.
  Do not use cross-parameter constraints on controller methods: Spring MVC 7.0 does not enforce
  them on their own (pinned by `GlobalExceptionHandlerTests#crossParameterOnlyViolationIsNotEnforcedBySpring`).
- Integration tests use `@Import(TestcontainersConfiguration.class)`; the PostgreSQL image there
  must match the one in `deploy/docker-compose.yml`.

### Database
- Schema changes only through Flyway migrations in `backend/src/main/resources/db/migration/`.
- A migration merged to `main` is **immutable**: fix or change things with a new migration.

### Internationalisation
- No hardcoded user-facing strings in the frontend: use i18n keys.
- Every new key must be added to **both** `en.json` and `es.json`.
- User-entered data (tournament names, notes) is never translated.

### Git workflow
- Never commit directly to `main`. One branch and one PR per issue.
- Branch name: `<type>/<issue-number>-<short-slug>` (e.g. `feat/10-games-crud`).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`, `ci:`).
- PR description links the issue with `Closes #N` and lists how it was verified.
- Features come with tests; keep CI green.

### Style
- Files are UTF-8 with LF line endings (see `.editorconfig` / `.gitattributes`).
- Java: 4-space indent. TypeScript, JSON, YAML, Markdown: 2-space indent.
