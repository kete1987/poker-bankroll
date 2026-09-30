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
| Frontend | React, TypeScript, Vite, Mantine, TanStack Query, react-i18next, ECharts |
| Deployment | Docker Compose (images published to GHCR, `amd64` and `arm64`) |

```
browser ──► web (nginx + SPA) ──/api──► api (Spring Boot) ──► db (PostgreSQL)
```

## Quick start

_Coming with the first release._ The goal is:

```bash
git clone https://github.com/kete1987/poker-bankroll.git
cd poker-bankroll/deploy
cp .env.example .env
docker compose up -d
```

> The app has no login. Run it on your local network and do not expose it to the internet
> without putting authentication in front of it.

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
