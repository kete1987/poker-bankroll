# AGENTS.md

Context and rules for AI coding agents (and humans) working on this repository.
`CLAUDE.md` only imports this file: keep everything here.

## Project

**poker-bankroll** is a self-hosted web app to manage a poker bankroll: record results of
tournaments, Sit&Go and spins (e.g. Expresso) and cash games, track the real balance of
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
| `frontend/` | Web app — React 19, TypeScript 7, Vite 8, React Router 8, TanStack Query 5, Mantine 9, react-i18next, ECharts 6 |
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
| `./mvnw spring-boot:run` | Run the API against the development database (`deploy/docker-compose.dev.yml`, `localhost:5433`) or `SPRING_DATASOURCE_*` |

With the API running: health at `http://localhost:8080/api/actuator/health`, Swagger UI at
`http://localhost:8080/api/swagger-ui.html`, OpenAPI spec at `http://localhost:8080/api/v3/api-docs`.

### Frontend (`frontend/`)

Requires Node 24 LTS. Run `npm ci` once.

| Command | What it does |
|---|---|
| `npm run dev` | Dev server on `:5173`; proxies `/api` to `http://localhost:8080` (override with `API_PROXY_TARGET`) |
| `npm test` | Run all tests once (Vitest + Testing Library, jsdom) |
| `npm run test:watch` | Tests in watch mode |
| `npm run typecheck` | Type-check with `tsc -b` |
| `npm run lint` | Lint with oxlint (warnings fail) |
| `npm run format` / `npm run format:check` | Format / check formatting with Prettier |
| `npm run build` | Type-check and build to `dist/` |

Before pushing frontend changes: `npm run typecheck && npm run lint && npm run format:check && npm test`.

### Docker (`deploy/`)

| Command | What it does |
|---|---|
| `docker compose -f deploy/docker-compose.dev.yml up -d` | PostgreSQL for development on `localhost:5433` (5433 avoids clashing with a local PostgreSQL) |
| `cd deploy && cp .env.example .env` | Create the stack configuration (set `POSTGRES_PASSWORD`) |
| `docker compose up -d --build` (in `deploy/`) | Build both images from the checkout and run db + api + web on `http://localhost:${WEB_PORT:-8080}` |
| `docker compose down` / `down -v` | Stop the stack / also delete the database volume |
| `docker compose exec backup /backup.sh` (in `deploy/`) | Take a database backup now (Git Bash: prefix `MSYS_NO_PATHCONV=1`) |

- Images: `backend/Dockerfile` (layered Spring Boot jar on Alpine with a Java runtime linked by
  `jlink`, user `app`) and `frontend/Dockerfile` (static build on `nginx-unprivileged`, port 8080).
  Both have a Docker `HEALTHCHECK`.
- The API runtime only has the JDK modules that `jdeps` finds plus a few added by hand (listed and
  explained in the Dockerfile). If the containerised API fails with `ClassNotFoundException` /
  `NoClassDefFoundError` for a `java.*`/`javax.*`/`jdk.*`/`com.sun.*` class that works with
  `./mvnw spring-boot:run`, add its module there.
- The nginx config is part of the web image: `frontend/nginx/default.conf.template` (SPA fallback,
  long cache for `/assets/`, `/api/` proxied to `${API_UPSTREAM}`, default `api:8080`, `/healthz`).
- Only `web` publishes a port; `api` and `db` are reachable only inside the Compose network.
- `backup` (`prodrigestivill/postgres-backup-local`, pinned tag) dumps the database daily to
  `BACKUP_DIR` (default `deploy/backups/`, git-ignored) with daily/weekly/monthly retention.
  Keep its PostgreSQL major in sync with the `db` image. Restore procedure: `docs/backups.md`.

## Domain glossary

- **Currency**: ISO 4217 code (`EUR`, `USD`). Belongs to the **room**, not to each game.
- **Room**: poker site account (Winamax, 888poker...) holding money in **one currency**; its games
  and movements are in that currency. Two currencies on the same site are two rooms.
- **Game type**: the format — `TOURNAMENT`, `SIT_AND_GO` (shown as "Sit & Go / Spins"; includes
  lottery Sit&Go such as Expresso) or `CASH`. Fixed list (table `game_type`, seeded by migrations):
  the application behaves differently per type.
- **Modality**: the poker game played — `NLHE` (default) or `PLO` (table `modality`). Independent
  of the type: any type and variant can be played in any modality.
- **Variant**: sub-type within a game type, in a single level (`KO`, `SPACE_KO`, `MYSTERY_KO`,
  `EXPRESSO`, `EXPRESSO_NITRO`, `DOUBLE_OR_NOTHING`, `DOUBLE_OR_NOTHING_DEMENTE`,
  `TRIPLE_OR_NOTHING`..., `HEADS_UP`, `REGULAR`). Known variants have a `code` translated by the
  frontend; users can add their own with a free-text `name`. Optional on a game; a game can only use
  variants of its own type.
- **Game**: one recorded result. `played_on` (date) is required, `played_at` (local start time) is
  optional and only used to order the games of a day.
  - `buy_in`: price of one entry, also when it was paid with a ticket (cash game: amount brought
    to the table).
  - `entries`: number of entries including re-entries (default 1).
  - `prize`: cash won, bounties apart (cash game: amount when leaving the table).
  - `bounty`: bounties/KO money won.
  - `ticket_prize_value` (+ optional `ticket_description`): value of a tournament ticket **won**
    as a prize (satellites). Unrelated to how the entry was paid.
  - `paid_with_ticket`: one entry was paid with a ticket instead of cash.
  - `notes`: free text (table or tournament ids, Expresso pot...).
  - **net** = `prize + bounty + ticket_prize_value − buy_in × entries`, a column generated by
    PostgreSQL.
  - Cash games are one sitting: one entry, no bounty or ticket fields (enforced by the database).
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
- Amounts are in the currency of their room, and API responses always carry that currency code
  next to the amounts. **Never sum amounts in different currencies**; aggregate per currency or
  convert explicitly (F-1).
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
- Lookup tables (`currency`, `game_type`, `modality`) use a stable upper-case code as primary key.
  Integrity rules live in the database too (CHECK constraints, foreign keys), not only in the API;
  schema tests are in `backend/src/test/java/.../schema/`.

### Frontend code
- Talk to the backend only through `src/api/client.ts` (`apiFetch`): it adds `/api`, sends the UI
  language as `Accept-Language` and turns error responses into `ApiError` (`status`, `code`,
  `message`, `errors`). Wrap calls in TanStack Query hooks next to it (see `src/api/health.ts`).
- UI components come from Mantine; icons from `@tabler/icons-react`; charts through `src/components/Chart.tsx`.
- Routes are declared in `src/routes.tsx`; tests render the real app with `renderApp(url)` from
  `src/test/renderApp.tsx` and stub `fetch` (`stubFetchJson`).
- The light/dark and language choices are stored in `localStorage` under `poker-bankroll.*` keys.

### Internationalisation
- No hardcoded user-facing strings in the frontend: use i18n keys (they are type-checked).
- Every new key must be added to **both** `frontend/src/locales/en.json` and `es.json`
  (`locales.test.ts` fails otherwise).
- User-entered data (tournament names, notes) is never translated.

### Git workflow
- Never commit directly to `main`. One branch and one PR per issue.
- Branch name: `<type>/<issue-number>-<short-slug>` (e.g. `feat/10-games-crud`).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`, `ci:`).
- PR description links the issue with `Closes #N` and lists how it was verified.
- Features come with tests; keep CI green.

### CI
- `.github/workflows/ci.yml` runs on every pull request and on pushes to `main`.
- On pull requests only the touched parts run: `backend/` → `./mvnw verify`; `frontend/` →
  `npm ci`, typecheck, lint, format check, tests, build. Changing the workflow runs both.
- The **`CI result`** job is the one to require in the branch ruleset: it passes when every job
  succeeded or was skipped because its part did not change.
- Actions are pinned to a full commit SHA with the version in a comment; update both together.

### Releases
- `.github/workflows/release.yml` publishes both images to GHCR (amd64 + arm64): `edge` on every
  push to `main`; `X.Y.Z`, `X.Y` and `latest` on a `vX.Y.Z` tag (pre-release tags only `X.Y.Z-pre`),
  plus the GitHub Release with generated notes.
- The git tag is the only source of the version (`-Drevision` for Maven, `APP_VERSION` for the web
  build). Do not edit versions by hand in `pom.xml` or `package.json`.
- Cut releases with `scripts/release.sh X.Y.Z` from an up-to-date `main`, or from GitHub with
  *Actions → Release → Run workflow*; both run `.github/scripts/release-preflight.sh`. The full flow
  (milestones, pre-releases, maintenance branches, deploying, rolling back) is in `docs/releasing.md`.
- Give each PR the same labels as its issue (`type:feature`, `type:chore` or `bug`; Dependabot adds
  `dependencies`): release notes are grouped by them (`.github/release.yml`).

### Dependabot
- `.github/dependabot.yml` opens weekly PRs for GitHub Actions, Maven (`backend/`), npm
  (`frontend/`), the Dockerfile base images and the PostgreSQL image in `deploy/`. Minor and patch
  updates come grouped per ecosystem; every major update has its own PR. New versions are only
  proposed 7 days after their release (security updates are not delayed).
- Merge a Dependabot PR only with CI green and the Codex review addressed. CI does not build the
  Docker images: for base image or `deploy/` updates, run `docker compose up -d --build` in `deploy/`.
- For a major update, read the release notes / migration guide and fix the code in the same PR.
- Kept in sync by hand (Dependabot does not update all the places):
  - PostgreSQL image: Dependabot updates `deploy/docker-compose.yml` and
    `deploy/docker-compose.dev.yml`; push the same change to `TestcontainersConfiguration.POSTGRES_IMAGE`
    on the PR branch. A PostgreSQL major also needs an upgrade path for existing data volumes.
  - Runtimes stay on LTS (Java 25, Node 24), so their majors are ignored in `dependabot.yml`:
    `eclipse-temurin` and `node` images, `@types/node`. Moving to the next LTS is a manual PR that
    updates the Dockerfiles, CI (`java-version`, `node-version`), `pom.xml` `java.version`,
    `package.json` `engines` and `@types/node` together.

### Style
- Files are UTF-8 with LF line endings (see `.editorconfig` / `.gitattributes`).
- Java: 4-space indent. TypeScript, JSON, YAML, Markdown: 2-space indent.
