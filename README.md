# poker-bankroll

**English** | [Español](README.es.md)

Self-hosted poker bankroll manager. Record the results of your tournaments, Sit&Go,
spins and cash games, keep track of your poker bankroll in each poker room, and see how
your results evolve over time.

> **Status: first version (0.1).** It is usable and young. What comes next is in the
> [issues](https://github.com/kete1987/poker-bankroll/issues) and the
> [project board](https://github.com/users/kete1987/projects/3).

## Screenshots

Taken with the [demo data](#try-it-with-demo-data): every result is made up.

| Dashboard | Games |
|---|---|
| ![Dashboard: net, ROI, ITM and bankroll, and the results per game type](docs/images/dashboard.png) | ![Games: quick start, games in play and the list of games with filters](docs/images/games.png) |
| **Statistics** | **Bankroll** |
| ![Statistics: net over time, month by month](docs/images/statistics.png) | ![Bankroll: deposits, result and its evolution per room](docs/images/bankroll.png) |

On a phone, lists become cards and the filters fold away, so nothing needs horizontal scrolling:

<img src="docs/images/phone-games.png" alt="The games on a phone, as cards" width="300">

## Why

Many players track results in a spreadsheet. It works until it doesn't: formulas break,
summaries have to be maintained by hand and different currencies are hard to mix.
poker-bankroll replaces that spreadsheet with a small app you run on your own machine.

It is **not** a hand-history analyser: tools like PokerTracker 4 already do that. The focus
here is results and bankroll management.

## Features

- Quick entry of games: tournaments (re-entries, bounties, ticket prizes), Sit&Go and spins
  (Expresso...) and cash games, in No-Limit Hold'em or PLO; duplicate a game, or add several
  alike at once (ten Expressos of an evening) with the result of each one in a row
- Templates of the games you play often: one click starts one
- Tags of your own on games ("challenge", "with friends"...), to filter and break down by them
- Dashboard with net result, ROI and ITM, broken down by game type
- Statistics by day, week, month or year with the net evolution chart, and broken down by room,
  type, variant, buy-in, tournament name, day of the week or tag
- Poker bankroll per room and currency: deposits, withdrawals, bonuses and the result of your
  games, and its evolution over time
- Multiple currencies (EUR and USD out of the box, extensible): each room keeps its own, and what
  mixes them is shown in a base currency, converted with the ECB rate of each day
  ([details](docs/currencies.md))
- English and Spanish UI, laid out for phones too
- Import of games from a CSV file ([format](docs/import.md)), to bring your history from a spreadsheet
- Export of the games and the bankroll movements you are looking at, as CSV or Excel
- Backup of everything to one file, downloaded and restored from the app: to move to another
  computer or to recover from a problem
- Daily database backups

See the [issues](https://github.com/kete1987/poker-bankroll/issues) for what is planned.

## Stack

| Part | Technology |
|---|---|
| Database | PostgreSQL 18 |
| Backend | Java 25, Spring Boot 4.1, Flyway, OpenAPI |
| Frontend | React 19, TypeScript 7, Vite 8, Mantine 9, TanStack Query, react-i18next, ECharts |
| Deployment | Docker Compose (images published to GHCR, `amd64` and `arm64`) |

```
browser ──► web (nginx + SPA) ──/api──► api (Spring Boot) ──► db (PostgreSQL)
```

## Quick start

Requires Docker with Compose:

```bash
git clone https://github.com/kete1987/poker-bankroll.git
cd poker-bankroll/deploy
cp .env.example .env   # set POSTGRES_PASSWORD
docker compose up -d   # runs the published images
```

Open `http://<your-host>:8080` (change the port with `WEB_PORT` in `.env`). Data lives in the
`poker-bankroll_db-data` Docker volume, which is kept when the stack is updated.

The images come from GHCR (`linux/amd64` and `linux/arm64`). Choose them with
`POKER_BANKROLL_VERSION`: `latest` (last stable release), a series such as `0.1` (fixes only), an
exact version such as `0.1.0`, or `edge` (last merge to `main`).

**Portainer:** create a stack, paste [`deploy/docker-compose.yml`](deploy/docker-compose.yml) and
set the variables of [`deploy/.env.example`](deploy/.env.example) (`POSTGRES_PASSWORD` at least,
and `BACKUP_DIR` as an absolute path of the host).

To build the images from the source instead:
`docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build`.
Versions, updates and rollbacks are explained in [docs/releasing.md](docs/releasing.md).

The API downloads the exchange rates of the European Central Bank from
[Frankfurter](https://frankfurter.dev) (`api.frankfurter.dev`), so it needs outgoing internet
access for them; everything else works without it. Set `EXCHANGE_RATES_ENABLED=false` in `.env`
to turn the downloads off (see [docs/currencies.md](docs/currencies.md)).

> The app has no login. Run it on your local network and do not expose it to the internet
> without putting authentication in front of it.

### Try it with demo data

To look at the app before using it for real, start it with a year of **made-up** results: four
rooms, about 400 games, games in play and bankroll movements, nothing real. It only needs Docker
with Compose: it runs the released images, nothing is built.

```bash
git clone https://github.com/kete1987/poker-bankroll.git
cd poker-bankroll/deploy
docker compose --env-file demo.env up -d
```

(Without git, it is enough to download `docker-compose.yml`, `docker-compose.demo.yml` and
`demo.env` from [`deploy/`](deploy) into one folder.)

Open `http://localhost:8081` (another port: change `WEB_PORT` in `demo.env`). When you are done,
throw the demo away with its data:

```bash
docker compose --env-file demo.env down -v
```

The demo is a Compose project of its own (`poker-bankroll-demo`, from
[`docker-compose.demo.yml`](deploy/docker-compose.demo.yml) on top of `docker-compose.yml`), with
its own database, a throwaway password and no backups. It does not read `.env` and never touches
a real installation, even one in the same folder.

To run it from the source instead, with the API and the web in development mode (requires
JDK 25, Node 24 and Docker):

```bash
# Terminal 1, from the repository root: API on :8080
cd backend && ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=demo
```

```bash
# Terminal 2, from the repository root: web on :5173
cd frontend && npm ci && npm run dev
```

The demo data is only loaded into an empty database, and never unless the `demo` profile is
active: a real installation ([Quick start](#quick-start)) always starts with an empty database.

## Backups

The stack backs up the database every day to `deploy/backups/` (keeping 7 daily, 4 weekly and
6 monthly dumps; set `BACKUP_*` in `.env` to change the folder, schedule or retention). In
Portainer, set `BACKUP_DIR` to an absolute path of the host. See [docs/backups.md](docs/backups.md)
to take a backup on demand, restore one and copy them to another machine.

Besides those, *Import / Export* in the app downloads a backup of everything as one file and
restores it, also into another installation: the easy way to move to another computer. See
[Backup from the app](docs/backups.md#backup-from-the-app).

## Repository layout

```
backend/    Spring Boot REST API
frontend/   React web app
deploy/     Docker Compose stack and nginx config
docs/       Documentation
```

## Contributing

Issues and pull requests are welcome. Read [AGENTS.md](AGENTS.md) for the conventions
(it applies to humans too).

## License

[MIT](LICENSE)
