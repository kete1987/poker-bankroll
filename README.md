# poker-bankroll

**English** | [Español](README.es.md)

Self-hosted poker bankroll manager. Record the results of your tournaments, Sit&Go,
Expresso and cash games, keep track of the real balance in each poker room, and see how
your results evolve over time.

> **Status: work in progress.** Nothing is runnable yet. Follow the
> [v0.1 MVP milestone](https://github.com/kete1987/poker-bankroll/milestone/1) and the
> [project board](https://github.com/users/kete1987/projects/3).

## Why

Many players track results in a spreadsheet. It works until it doesn't: formulas break,
summaries have to be maintained by hand and different currencies cannot be mixed.
poker-bankroll replaces that spreadsheet with a small app you run on your own machine.

It is **not** a hand-history analyser: tools like PokerTracker 4 already do that. The focus
here is results and bankroll management.

## Planned features (v0.1)

- Quick entry of games: tournaments (re-entries, bounties, ticket prizes), Sit&Go,
  Expresso (with multiplier) and cash games
- Dashboard with net result, ROI and ITM, broken down by game type
- Daily and monthly results, net evolution chart
- Bankroll per room: deposits, withdrawals, bonuses and reconciliation with the real balance
- Multiple currencies (EUR and USD out of the box, extensible)
- English and Spanish UI
- Import from an existing spreadsheet (CSV)
- Daily database backups

See the [issues](https://github.com/kete1987/poker-bankroll/issues) for the full roadmap.

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

Requires Docker with Compose. The app is still a skeleton, but the stack already runs:

```bash
git clone https://github.com/kete1987/poker-bankroll.git
cd poker-bankroll/deploy
cp .env.example .env          # set POSTGRES_PASSWORD
docker compose up -d --build  # builds the images from the source
```

Open `http://<your-host>:8080` (change the port with `WEB_PORT` in `.env`). Data lives in the
`poker-bankroll_db-data` Docker volume.

Once releases are published, `docker compose pull && docker compose up -d` will run the ready-made
images from GHCR instead of building them (choose the version with `POKER_BANKROLL_VERSION`).
In Portainer, create a stack from `deploy/docker-compose.yml` and set the variables of `.env.example`.

> The app has no login. Run it on your local network and do not expose it to the internet
> without putting authentication in front of it.

## Backups

The stack backs up the database every day to `deploy/backups/` (keeping 7 daily, 4 weekly and
6 monthly dumps; set `BACKUP_*` in `.env` to change the folder, schedule or retention). In
Portainer, set `BACKUP_DIR` to an absolute path of the host. See [docs/backups.md](docs/backups.md)
to take a backup on demand, restore one and copy them to another machine.

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
