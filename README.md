# poker-bankroll

**English** | [Español](README.es.md)

Self-hosted poker bankroll manager. Record the results of your tournaments, Sit&Go,
spins and cash games, keep track of your poker bankroll in each poker room, and see how
your results evolve over time.

> **Status: first version (0.1).** It is usable and young. What comes next is in the
> [issues](https://github.com/kete1987/poker-bankroll/issues) and the
> [project board](https://github.com/users/kete1987/projects/3).

## Why

Many players track results in a spreadsheet. It works until it doesn't: formulas break,
summaries have to be maintained by hand and different currencies cannot be mixed.
poker-bankroll replaces that spreadsheet with a small app you run on your own machine.

It is **not** a hand-history analyser: tools like PokerTracker 4 already do that. The focus
here is results and bankroll management.

## Features

- Quick entry of games: tournaments (re-entries, bounties, ticket prizes), Sit&Go and spins
  (Expresso...) and cash games, in No-Limit Hold'em or PLO
- Dashboard with net result, ROI and ITM, broken down by game type
- Daily and monthly results, net evolution chart
- Poker bankroll per room and currency: deposits, withdrawals, bonuses and the result of your games
- Multiple currencies (EUR and USD out of the box, extensible)
- English and Spanish UI
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

> The app has no login. Run it on your local network and do not expose it to the internet
> without putting authentication in front of it.

### Try it with demo data

To look at the app without recording anything, run it from the source with a throwaway database
filled with a year of made-up results (requires JDK 25, Node 24 and Docker):

```bash
# Terminal 1, from the repository root: API on :8080
cd backend && ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=demo
```

```bash
# Terminal 2, from the repository root: web on :5173
cd frontend && npm ci && npm run dev
```

The demo data is only loaded into an empty database, and never unless the `demo` profile is active.

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
