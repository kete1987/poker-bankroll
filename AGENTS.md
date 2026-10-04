# AGENTS.md

Context and rules for AI coding agents (and humans) working on this repository.
`CLAUDE.md` only imports this file: keep everything here.

## Project

**poker-bankroll** is a self-hosted web app to manage a poker bankroll: record results of
tournaments, Sit&Go and spins (e.g. Expresso) and cash games, track the poker bankroll of
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
| `deploy/` | `docker-compose.yml` (db + api + web + backup, released images), `docker-compose.build.yml` (builds them from the checkout), `.env.example` |
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
| `./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true` | Rewrite `frontend/openapi.json` from the API (after changing an endpoint, request or response) |
| `./mvnw spring-boot:test-run` | Run the API on `:8080` against a throwaway PostgreSQL container |
| `./mvnw spring-boot:test-run -Dspring-boot.run.profiles=demo` | Same, with a year of made-up data (see "Demo data") |
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
| `npm run api:types` | Regenerate `src/api/schema.d.ts` from `openapi.json` (run `npm ci --prefix tools/api-types` once) |
| `npm run api:check` | Fail if `src/api/schema.d.ts` is not what `openapi.json` generates |
| `npm run lint` | Lint with oxlint (warnings fail) |
| `npm run format` / `npm run format:check` | Format / check formatting with Prettier |
| `npm run build` | Type-check and build to `dist/` |

Before pushing frontend changes: `npm run typecheck && npm run lint && npm run format:check && npm test`.

### Docker (`deploy/`)

| Command | What it does |
|---|---|
| `docker compose -f deploy/docker-compose.dev.yml up -d` | PostgreSQL for development on `localhost:5433` (5433 avoids clashing with a local PostgreSQL) |
| `cd deploy && cp .env.example .env` | Create the stack configuration (set `POSTGRES_PASSWORD`) |
| `docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build` (in `deploy/`) | Build both images from the checkout and run db + api + web on `http://localhost:${WEB_PORT:-8080}` |
| `docker compose up -d` (in `deploy/`) | Run the released images from GHCR (`POKER_BANKROLL_VERSION`, default `latest`) |
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
- `docker-compose.yml` has no `build` sections on purpose: it is also pasted as a Portainer stack,
  where there is no source code to build from. Building lives in `docker-compose.build.yml`.
- `backup` (`prodrigestivill/postgres-backup-local`, pinned tag) dumps the database daily to
  `BACKUP_DIR` (default `deploy/backups/`, git-ignored) with daily/weekly/monthly retention.
  Keep its PostgreSQL major in sync with the `db` image. Restore procedure: `docs/backups.md`.

## Domain glossary

- **Currency**: ISO 4217 code (`EUR`, `USD`). Belongs to the **room**, not to each game.
- **Room**: poker site account (Winamax, 888poker...) holding money in **one currency**; its games
  and movements are in that currency. Two currencies on the same site are two rooms. The currency
  of a room cannot change once it has games or bankroll movements (database trigger).
  - A room can have a **logo**: a PNG, JPEG or WebP image of at most 256 kB (no SVG: it can carry
    scripts), uploaded by the user and stored in the database (table `room_logo`, apart from `room`
    so that reading rooms never loads images; deleted with its room). The format is detected from
    the content, not from the declared `Content-Type`. Rooms carry a `logoVersion` (`null` without
    logo) that changes with every upload; the image is `GET /rooms/{id}/logo?v=<logoVersion>`,
    cacheable forever under that URL. A logo does not make a room "in use".
- **Inactive** rooms and variants keep their history and stay in statistics, but take no new games:
  the UI does not offer them and the API rejects creating a game in them, or moving one to them
  (`ROOM_INACTIVE`, `VARIANT_INACTIVE`). A game already there can still be edited.
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
  - `status`: `IN_PLAY` (registered when it starts, usually with just type and buy-in; no prize,
    bounty or ticket yet) or `FINISHED` (the result is known, possibly nothing won). A game in play
    already counts in net and in the bankroll (its buy-in is already spent) but **not in
    result statistics** (games played, ITM, ROI), which only count finished games.
  - Actions on a game in play: **finish** (set the result), **re-entry** (one more entry; tournaments
    and Sit&Go) and **rebuy** (more money brought to the table, added to `buy_in`; cash games).
  - `buy_in`: price of one entry, also when it was paid with a ticket (cash game: amount brought
    to the table).
  - `entries`: number of entries including re-entries (default 1).
  - `prize`: cash won, bounties apart (cash game: amount when leaving the table).
  - `bounty`: bounties/KO money won.
  - `ticket_prize_value` (+ optional `ticket_description`): value of a tournament ticket **won**
    as a prize (satellites). Informative only: a ticket is not money until it is played.
    Unrelated to how the entry was paid.
  - `paid_with_ticket`: one entry was paid with a ticket instead of cash, so it cost no money
    (re-entries are paid in cash).
  - `notes`: free text (table or tournament ids, Expresso pot...).
  - **net** = `prize + bounty − buy_in × (entries − (paid_with_ticket ? 1 : 0))`, a column
    generated by PostgreSQL. It is **real money**: tickets count neither when won nor when used,
    whatever their origin (won in a satellite, gift from the room...), so the sum of net is always
    the cash result of the games and equals their effect on the bankroll.
  - Cash games are one sitting: one entry, no bounty or ticket fields (enforced by the database).
- **ITM** (in the money): `prize > 0` or `ticket_prize_value > 0`. **With prize** also counts
  bounties (ITM or `bounty > 0`). Neither applies to cash games, nor does the average buy-in: in
  totals mixing types they are computed on the other games only.
- **Winning game**: `net > 0` (any type).
- **Invested** = money paid for entries: `buy_in × (entries − (paid_with_ticket ? 1 : 0))`.
- **Won** = `prize + bounty`. Tickets won are reported apart, never added to money.
- **ROI** = net / invested (unknown when nothing was invested).
- **Average buy-in**: mean of `buy_in` over the games, not a mean of per-type means.
- Statistics (`stats` package) share one set of figures (`StatsFigures`) built from sums by
  `GameTotals`; rates and ROI are fractions with 4 decimals (`0.3496`), formatted by the frontend.
  `/stats/summary` gives them overall and per game type, `/stats/groups?groupBy=` per period (`DAY`,
  `WEEK` from Monday, `MONTH`, `YEAR`), `GAME_TYPE`, `VARIANT`, `ROOM`, `MODALITY`, `BUY_IN`,
  `BUY_IN_RANGE` (fixed ranges: free, below 1, and from 1, 2, 5, 10, 20 and 50; each one leaves its
  upper end out), `NAME` (ignoring case and surrounding spaces, written as most of its games write
  it; games without a name are one group, listed last) or `WEEKDAY` (1 Monday to 7 Sunday); both
  take the filters of the games list. With `byGameType=true` each group is also broken down by
  game type. Periods carry the **cumulative net**, which starts from zero
  at the beginning of the filtered range. A new grouping is a `GroupBy` constant plus its `Grouping`
  in `StatsService`.
- **Bankroll**: the money set aside for poker and what was won or lost with it. It is **not the
  balance of the room account** (which may hold money for other products, e.g. sports betting) and
  is never reconciled with it: with no movements recorded, the bankroll of a room is just its
  result, negative when losing.
  - **Result** = net of the games (those in play included) + bonuses.
  - **Bankroll** = deposited − withdrawn + adjustments + result, per room and per currency.
  - `GET /bankroll/summary` with dates gives the figures of that period (the result is what was won
    or lost in it); without them, the bankroll as it is now.
  - `GET /bankroll/evolution?groupBy=DAY|WEEK|MONTH|YEAR` gives, per currency, the total and each
    room, the bankroll period by period: a `startingBankroll` (everything before `from`) and, for
    each period with movements or games, what changed it and the bankroll at its end. The last one
    without dates is the bankroll of the summary (pinned by `BankrollEvolutionApiTests`). Periods
    are cut by `stats/TimePeriod`, shared with `/stats/groups`.
- **Bankroll movement**: `DEPOSIT` (money set aside for poker; the first one is the initial
  bankroll), `WITHDRAWAL`, `BONUS` (poker money not coming from a game: rakeback, promotions) or
  `ADJUSTMENT` (manual correction). The amount is positive and the type gives its direction; only
  an adjustment can be negative (`signed_amount` is generated by PostgreSQL).
  - It belongs to a **room** (and is in its currency) or to **no room**, carrying its own
    currency: then it only counts in the total of that currency (e.g. an initial bankroll not
    split by room).
  - Unlike games, movements are accepted in inactive rooms.
- **Backup**: two different things. The **backup from the app** is one JSON file with everything
  the user created (rooms and logos, user-defined variants and which built-in ones are active,
  every game, every bankroll movement), downloaded and restored in the Import / Export section;
  restoring it **replaces everything** the installation holds. The **automatic backups** are the
  `pg_dump` files of the `backup` service of the stack. An installation is **empty** when it has
  no rooms, user-defined variants, games or movements.

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
  Business errors throw `ApiException(ErrorCode, args...)`; one about an item of a request with
  several (a batch of games) is `ex.atIndex(i)` and its problem carries that `index` (zero-based).
  The `detail` is resolved from `messages.properties` (English, default) /
  `messages_es.properties` using `Accept-Language`; add every new key to both files.
- Lists that can grow are paginated with `page` (zero-based) and `size`, and return a
  `PageResponse` (`items`, `page`, `size`, `totalItems`, `totalPages`). Sorting uses
  `sort=<field>,<asc|desc>` with a whitelist of fields and always a total order (see `game/GameSort`).
  Aggregates are the exception: `/stats/groups` returns every group, because a chart needs the whole
  series and the cumulative net of a page would be meaningless. Its size is bounded by the grouping
  (at most one small row per day played), and `from`/`to` narrow it.
- A filter that takes several values (`gameType`, `roomId`, `variantId` of the games filters) is a
  `List` parameter: repeated or comma-separated, its values combined with OR, the filters with each
  other with AND; empty is no filter.
- Optional fields omitted in a request take their documented default; `PUT` replaces the whole resource.
- State changes that are a single user gesture are their own `POST` sub-resource instead of a
  full `PUT` (e.g. `/games/{id}/finish`, `/games/{id}/re-entries`, `/games/{id}/rebuys`); they return
  the updated resource and fail with a specific `409` code when the resource is not in the right state.
- Every change to a game loads it with `GameRepository.findForUpdateById` (row lock), so simultaneous
  requests on the same game (a double click, a re-entry racing a finish) run one after another and
  none is lost.
- `GET /games/names?q=&gameType=&limit=` suggests names of recorded games while one is typed: those
  containing `q` (ignoring case, literally; nothing below 2 characters), most used first. Names that
  differ only in case or surrounding spaces are one, written as in its most recent game, whose
  buy-in (with its `currencyCode`), variant and modality come with it. It is a plain list bounded by `limit` (8, at most 20),
  not a `PageResponse`.
- `POST /games/batch` records 1 to 50 games (`GameBatchRequest.MAX_GAMES`) in one transaction, all
  of them or none, each one through `GameService.create` (same rules as `POST /games`), in the order
  sent, so their ids follow it. It answers `201` with them in that order (`GameBatchResponse`).
  Validation errors name the game (`games[3].prize`); a business error of one game has its `index`.
- Recording a game or a bankroll movement in a room loads it with `RoomRepository.findToRecordInById`
  (shared row lock), so a simultaneous change of the room's currency waits and is rejected.
- A body that is not JSON (the logo of a room) is read from the `InputStream` up to its limit plus
  one byte, never as `@RequestBody byte[]`, which would load whatever is sent; its content is
  described by hand in `@Operation(requestBody = ...)`.
- `POST /imports/games` (`gameimport` package) imports games from a CSV file sent as the body
  (`text/csv`), in the one format of the application, described for users in `docs/import.md`: keep
  that document and `frontend/public/import-example.csv` (the example the web app offers, imported
  by a backend test) in step with `GameCsv`. A file
  that cannot be read as a whole is an error (`IMPORT_*` codes); errors of rows come in the `200`
  response (`errors`, with `imported: false`), each with its row, column, `code` and message.
  It is all or nothing, and `dryRun=true` only checks. Rows are recorded through `GameService`,
  `RoomService` and `VariantService`, so the rules are those of the API, and the transaction is
  rolled back on a dry run or when a row failed: do not add a second validation path for imports.
- `GET /exports/games` and `GET /exports/movements` (`export` package) give, as a file to download,
  **every** game or movement the filters select (the filter parameters of their lists, without page
  or order), oldest first, as `format=CSV` or `XLSX`. Rules:
  - Only **finished** games are exported, in both formats; the status is not a filter there.
  - The CSV of games is the format of the import, written by `GameCsv.Writer` next to what reads
    it: exporting and importing into an empty database gives the same games (pinned by
    `ExportApiTests`). A new column of a game goes in `GameCsv`, both ways, and in `docs/import.md`.
  - The CSV of movements has its own columns (`ExportService.MOVEMENT_CSV_COLUMNS`, the amount
    signed); there is no import for it.
  - Excel files are made to be read: typed cells (`export/ExcelSheet`), and headers and values in
    the language of `Accept-Language`, from the `export.*` keys of `messages*.properties`. Every
    built-in variant needs its `export.variant.<CODE>` there (a test fails otherwise), with the
    words the frontend uses (`variants` in `locales/*.json`). What the user wrote is not translated.
  - They are written with `fastexcel`, which only needs `java.base`. Do not use Apache POI (AWT,
    fonts) without proving it works in the API image: build it and export from the container.
  - Rows are read from the database through a cursor (`GameService.forEach`,
    `BankrollService.forEach`), never as one list; the file is built in memory.
- `GET /backup` and `POST /backup/restore?dryRun=&replace=` (`backup` package) back up everything
  the user created into one JSON file and restore it, replacing **everything** (described for
  users in `docs/backups.md`: keep it in step). Rules:
  - The file says the version of its format (`formatVersion`). Each version has its own records
    (`BackupV1`), which are the format and **never change once released**; they are mapped to and
    from `BackupData`, the model the restore works on. A change of the format is a new
    `BackupV2` next to it, `BackupFormat.CURRENT_VERSION` and `write` moved to it and a case in
    `BackupFormat.read`: every older version stays readable, a newer one is refused
    (`BACKUP_FORMAT_TOO_NEW`). A new column of a game, a room... that must survive a backup goes
    in `BackupData` and in the records of the current version (optional there, so files made
    before it still restore), both ways in `BackupService`.
  - Rooms and variants have ids that only mean something inside the file; built-in variants are
    named by game type and code and only their `active` is restored (the ones the file does not
    name are active). Amounts are JSON numbers read as `BigDecimal`. The mapper is the one of
    `BackupFormat`, not the one of the API.
  - A restore is all or nothing, in one transaction that locks the tables. The file is checked
    as a whole first, with the constraints of the request records (`GameRequest`,
    `MovementRequest`...) and its own (`BackupProblem`); errors come in the `200` response
    (`errors`, with `restored: false`), each with its place in the document (`games[12].buyIn`).
    Then everything is deleted and written through the **entities** (the games, which are many,
    with plain SQL in batches: `BackupService.writeGames`), not through the services
    that record by hand: a backup holds states they refuse (games in inactive rooms or with
    inactive variants). `dryRun=true` does the same and rolls back.
  - An installation that has data is only replaced with `replace=true`
    (`BACKUP_REPLACE_NOT_CONFIRMED` otherwise); a dry run never needs it. This is the one
    destructive operation of the application: do not make it easier to trigger.
  - The file is read into memory, 32 MB at most (`BackupService.MAX_BYTES`); the nginx of the web
    image allows bodies up to 40 MB (`client_max_body_size`): keep it above that limit.
- Controller method names are the `operationId`s of the contract: keep them unique across
  controllers (`getLogo`, not a second `get`), or springdoc renumbers the ones of other endpoints.
- The OpenAPI spec is the contract, and it is committed as `frontend/openapi.json` (sorted keys,
  without `servers` and the version). The frontend types are generated from that file, so two
  checks keep everything in sync: `OpenApiContractTests` fails when the file is not what the API
  serves, and `npm run api:check` (CI) when `src/api/schema.d.ts` is not what the file generates.
  **After changing the API**: update the contract (`-Dopenapi.update=true`, see Commands), run
  `npm run api:types`, and commit both files with the change.
- Request and response records describe themselves: a component is `required` in the contract
  unless it is `@Nullable` (then it also accepts `null`), done by `common/openapi/RecordSchemaConverter`.
  So annotate every optional component with `@Nullable`, and give nested records a name that is
  clear on its own (`BankrollFigures`, not `Figures`): it becomes the schema and type name.
  Endpoints that create a resource declare `@ApiResponse(responseCode = "201")`.

### Backend code
- Base package `io.github.kete1987.pokerbankroll`, organised **by feature**
  (`catalog`, `room`, `variant`, `game`, `bankroll`, `stats`...), with cross-cutting code in `common`.
- A feature has a JPA entity, a Spring Data repository, a `@Service` with the rules (transactional,
  returns response records) and a thin package-private `@RestController`. Requests and responses are
  Java records (`XxxRequest`, `XxxResponse`); entities never leave the service.
- `GameType` and `Modality` are Java enums mirroring their lookup tables (a test keeps them in sync);
  adding a value needs a migration and the enum constant.
- Expected business conflicts are checked in the service and thrown as `ApiException` with a specific
  `ErrorCode` (e.g. `ROOM_IN_USE`); add the code to the enum and its message to both
  `messages*.properties`. Database constraint violations that slip through become a generic `409 CONFLICT`.
- "Is this row used?" (by games; rooms also by bankroll movements) is answered with native queries
  (`RoomRepository.isInUse`),
  exposed to clients as `inUse` so they can offer deactivate instead of delete.
- Configuration comes from `application.yaml`; override it with standard Spring environment
  variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`...).
- Hibernate never changes the schema (`ddl-auto: validate`); Flyway owns it.
- A `LocalTime` attribute is mapped with `@JdbcTypeCode(SqlTypes.LOCAL_TIME)` (see `Game.playedAt`):
  by default Hibernate sends it as `java.sql.Time` and shifts it from the time zone of the JVM to
  `hibernate.jdbc.time_zone` (UTC), so the stored time would depend on where the API runs
  (pinned by `GameTimeZoneApiTests`).
- Validate input with Bean Validation on request DTOs (`@Valid @RequestBody`) and on simple
  parameters. Rules spanning several fields go in a **class-level constraint on the DTO** that
  reports each offending field (see `game/CashGameFields`): the annotation name is the `code`
  clients receive, and its message goes in both `messages*.properties` under that same name.
  Rules that need the database (does the room exist?) are checked in the service.
  Do not use cross-parameter constraints on controller methods: Spring MVC 7.0 does not enforce
  them on their own (pinned by `GlobalExceptionHandlerTests#crossParameterOnlyViolationIsNotEnforcedBySpring`).
- API tests extend `ApiIntegrationTest` (whole application on PostgreSQL through `MockMvcTester`,
  paths relative to `/api`, test rows deleted after each test): one `XxxApiTests` per feature.
  The PostgreSQL image in `TestcontainersConfiguration` must match the one in `deploy/docker-compose.yml`.

### Demo data
- The Spring profile `demo` (`demo/DemoDataSeeder`) fills an **empty** database on startup with a
  year of made-up results ending today: four rooms (EUR and USD, one inactive, three with a logo), a user-defined
  variant, about 400 games of every type, three games in play and bankroll movements. It does
  nothing when the database already has a room, a game, a movement or a user-defined variant, and
  is never active by default.
- It creates everything through the services, so it also exercises the rules of the API. When a
  feature adds data worth seeing in the UI, add it to the seeder.
- The logos of the demo rooms are invented shapes drawn in code (`demo/DemoLogo`): the real logos
  of poker rooms are trademarks and are never shipped. The PNG is written by hand, because the
  runtime of the API image has no `java.desktop` (`ImageIO`, `java.awt`).
- To look at the frontend with data: run the API with the command above and `npm run dev` in
  `frontend/`, then open `http://localhost:5173`.

### Database
- Schema changes only through Flyway migrations in `backend/src/main/resources/db/migration/`.
- Migrations are **immutable from the first release (0.1.0)**: after it, fix or change things with a
  new migration. Until then the schema is still being shaped and is consolidated in place
  (`V2__initial_schema.sql`); a database created by an earlier build (development, `edge`) fails
  Flyway's checksum validation and must be recreated (`docker compose down -v`).
- Lookup tables (`currency`, `game_type`, `modality`) use a stable upper-case code as primary key.
  Integrity rules live in the database too (CHECK constraints, foreign keys), not only in the API;
  schema tests are in `backend/src/test/java/.../schema/`.

### Frontend code
- Types of requests and responses come from `src/api/types.ts`, aliases over the generated
  `src/api/schema.d.ts` (never edited by hand; add an alias when a new shape is used). The generator
  lives in `tools/api-types/` with its own `package.json`: `openapi-typescript` needs TypeScript 5
  and the app uses TypeScript 7.
- Talk to the backend only through `src/api/client.ts` (`apiFetch`): it adds `/api`, sends the UI
  language as `Accept-Language` and turns error responses into `ApiError` (`status`, `code`,
  `message`, `errors`, `index`). Wrap calls in TanStack Query hooks next to it (see `src/api/health.ts`).
- UI components come from Mantine; icons from `@tabler/icons-react`; charts through `src/components/Chart.tsx`.
- Sections of the app are listed once in `src/layout/navigation.ts` (menu and routes), each with
  its page in `src/pages/` (`routes.tsx`). Wrap every page in `components/Page` (heading and
  browser tab title).
- Format every date, number, percentage and amount with `useFormat()` (`src/format/`), never by
  hand: it follows the UI language. Rates come from the API as fractions; `null` is shown as `—`.
- Routes are declared in `src/routes.tsx`; tests render the real app with `renderApp(url)` from
  `src/test/renderApp.tsx` and stub the backend with `stubApi({ 'GET /rooms': [...], 'POST /games':
  (call) => ... })`, which returns the calls made (`stubFetchJson` for a single response). Find
  elements by role and name; options of a `Select` and content of dialogs are not "visible" for
  jsdom (`hidden: true`, `toBeInTheDocument`).
- Forms use `@mantine/form`: required fields are checked before sending, validation errors of the
  backend (`ApiError.errors`) are set on their fields and any other error is shown in an alert
  (see `games/GameForm.tsx`). Success is confirmed with a notification (`@mantine/notifications`).
- Number inputs give text for some values (`8.40` keeps its zero): read them with `amountOrNull`
  (`games/amount.ts`), never with `typeof value === 'number'`.
- Dialogs with one action use `components/useSubmit` (one run at a time, failure message) and
  `components/ConfirmDialog` for a plain confirmation; the close button of a modal is labelled
  `actions.close`, so it is not confused with a "Cancel" button.
- Filters, order and page of a list live in the URL (`games/useGameFilters.ts`): they survive a
  reload and the back button. Lists are written with commas (`room=1,2`). Invalid values in the
  URL are ignored.
- Type, room and variant are chosen with `games/ScopeFilters`, and anything read from the URL goes
  through `components/urlParams` (invalid values are dropped there).
- The name of a game is a `games/NameInput` fed by `games/useNameSuggestions` (debounced, from 2
  characters, for the type of the form), shared by the game form and the bulk add. Picking a name
  fills the buy-in, variant and modality of a **new** game, except the ones the user has set by hand
  in that form (give such a field its props with `filledByName`); a game being edited only takes
  the name. The buy-in is only filled when it is in the currency of the chosen room, and is emptied
  again if the room then changes to another currency.
- **Duplicate** (an action of every game) opens the add game form with `copyOf`: room, type,
  variant, modality, name and buy-in of the game, today, the status a new game gets, and nothing of
  its result, entries or notes. What it copied counts as set by hand (a name picked afterwards does
  not replace it), its buy-in is emptied if the room changes to another currency, and an inactive
  room or variant is left to choose.
- **Add several** (`games/BulkAddForm.tsx`) records 2 to 50 tournaments or Sit & Go alike in one
  `POST /games/batch`: what they share once, then one row per game (prize, bounties for tournaments,
  notes; an empty prize is 0; only notes when they are in play) and the totals of what is about to
  be recorded. Errors of the backend go on the row of their game (`games[2].prize` is row 3).
- Charts are built as an ECharts option passed to `components/Chart` (register there the ECharts
  components a new chart needs). Colouring a line by value needs closed ranges in `visualMap`.
  Tests replace `Chart` with a stub and assert on the option (see `pages/StatsPage.test.tsx`).
- The statistics screen has two views kept in the URL (`view`): results over time, and breakdowns
  (`stats/Breakdown.tsx`) by room, type, variant, modality, buy-in range, tournament name or day of
  the week, as bars and a table sorted on the client (`by`, `sort=<column>,<asc|desc>` in the URL).
  A new breakdown is a `GroupBy` of the backend, its entry in `DIMENSIONS` (`stats/useStatsFilters.ts`),
  its label in `Breakdown` and its name in both locale files. Names are asked for tournaments
  unless the filter already names types or variants.
- The bankroll screen draws the bankroll over time (`bankroll/BankrollEvolutionChart.tsx`): one
  line per room, the total on top with deposits and withdrawals marked, for the period, rooms and
  currency of the page. The cut (`group` in the URL) is automatic unless chosen: from the length
  of the period, or, with an open end, from when there was activity, which the evolution by months
  says first (`bankroll/evolution.ts`).
- **Phones**: below the `sm` breakpoint (`components/useNarrowScreen`) nothing may need horizontal
  scrolling. The hook chooses what is rendered (not CSS that hides one of two copies, which tests
  and screen readers would both see):
  - Lists of things (games, games in play, movements) become **cards**, one per row
    (`games/GameCards`, `bankroll/MovementCards`), with their actions in a menu; the order of the
    games is chosen above the cards, as there are no column headings.
  - Tables of figures become a `components/CompactTable`: the columns that matter most (played,
    net, ROI; result and bankroll for rooms) and the rest of each row unfolded on demand.
  - The filters of a screen go in `components/FilterBar`: the period stays in sight and the others
    fold under a "Filters" button that says how many are set.
  - Large dialogs open full screen (`fullScreen={narrow}`).
  A new table or row of filters needs its phone layout; tests run as on a phone with
  `onANarrowScreen()` (`test/narrowScreen.ts`, see `pages/Phone.test.tsx`).
- A period is chosen with `components/PeriodFilter` (`components/period.ts` has the predefined
  ranges, from today to all time, in the time zone of the browser; this week goes from Monday to
  Sunday, like the `WEEK` grouping). `periodOf` names the range of the URL, so no two predefined
  periods may give the same dates. A screen shows one currency at a time: amounts in different
  currencies are never added.
- A chart over time is not drawn for a single day (`isSingleDay`): the statistics keep their cards
  and table without it, and the bankroll screen leaves its evolution out.
- A logo can also come from a URL: the browser cannot read images of other sites, so
  `POST /rooms/logo-fetch` downloads it (`room/RemoteImageFetcher`) and hands it back; it then
  follows the same path as a file. That endpoint only fetches `http`/`https` URLs of **public**
  addresses, also after redirects (`room/PublicAddress`): the app has no login and must not be a
  way into the local network. The HTTP client (Apache HttpClient 5) gets its addresses from the
  application, so the ones checked are the ones connected to; the whole download has a deadline
  (`poker-bankroll.logo-fetch.timeout`, 20 s). `poker-bankroll.logo-fetch.allow-private-addresses=true`
  lifts the address check (the tests need it to reach their own web server).
- A list is exported with `components/ExportMenu` (CSV or Excel), given the function of
  `api/exports.ts` that asks for the file with the filters the list has, but neither its page nor
  its order. The file is fetched through `apiFetchFile` (so the Excel comes in the UI language and
  errors are `ApiError`s) and saved with `components/saveFile`, under the name the backend gives
  it. The menu says what is left out (games in play), and so does the notification afterwards.
  Tests stub `URL.createObjectURL` and the click of the link (`pages/Export.test.tsx`).
- The Import / Export section (`pages/ImportPage.tsx`, route `/import`) has two parts: the import
  of games from a CSV file and the backup of everything.
  - The import (`api/imports.ts`) sends the chosen CSV file as it is: first with `dryRun=true`, to
    show what it holds and its errors, and the import is only offered when there are none. After
    importing, the page is left without file. It does not read or validate the file itself: the
    format lives in the backend and in `docs/import.md`.
  - The backup (`backup/BackupSection.tsx`, `api/backup.ts`) downloads the file like an export
    and restores one the same way as the import: the chosen file is checked with `dryRun=true`
    and the page shows what it holds next to what the installation holds. When the installation
    has data, a red alert says what will be deleted and the confirmation
    (`ConfirmDialog` with `confirmDisabled`) only goes on once a box is ticked; `replace=true` is
    only sent then. Restoring invalidates every query.
- A logo is resized in the browser before it is uploaded (`settings/resizeImage.ts`, 128 px at
  most, PNG), so the backend only stores small images. Tests replace that module: canvas does not
  exist in jsdom.
- Changing a room or a variant invalidates every query (`api/rooms.ts`, `api/variants.ts`): they
  are named all over the app.
- A room is always rendered with `components/RoomLabel`: its logo (or its initial when it has
  none) and its name. It takes the `logoVersion` from the shared list of rooms (`useRooms`).
- A mutation invalidates every query its data affects (a game changes `games`, `stats` and
  `bankroll`): see `api/games.ts`.
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
- On pull requests only the touched parts run: `backend/` (or `frontend/openapi.json`) →
  `./mvnw verify`; `frontend/` → `npm ci`, generated API types check, typecheck, lint, format
  check, tests, build. Changing the workflow runs both.
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
  (`frontend/` and `frontend/tools/api-types/`), the Dockerfile base images and the PostgreSQL image in `deploy/`. Minor and patch
  updates come grouped per ecosystem; every major update has its own PR. New versions are only
  proposed 7 days after their release (security updates are not delayed).
- Merge a Dependabot PR only with CI green and the Codex review addressed. CI does not build the
  Docker images: for base image or `deploy/` updates, build and run the stack from the checkout
  (see Commands).
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
