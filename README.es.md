# poker-bankroll

[English](README.md) | **Español**

Gestor de banca de póker autoalojado. Registra los resultados de tus torneos, Sit&Go,
Expresso y partidas de cash, lleva el saldo real de cada sala y consulta cómo evolucionan
tus resultados.

> **Estado: en desarrollo.** Todavía no hay nada ejecutable. Puedes seguir el
> [milestone v0.1 MVP](https://github.com/kete1987/poker-bankroll/milestone/1) y el
> [tablero del proyecto](https://github.com/users/kete1987/projects/3).

## Por qué

Muchos jugadores llevan sus resultados en una hoja de cálculo. Funciona hasta que deja de
hacerlo: las fórmulas se rompen, los resúmenes hay que mantenerlos a mano y no se pueden
mezclar monedas. poker-bankroll sustituye esa hoja por una pequeña aplicación que ejecutas
en tu propia máquina.

**No** es un analizador de manos: para eso ya existen herramientas como PokerTracker 4.
Aquí el foco son los resultados y la gestión de la banca.

## Funcionalidades previstas (v0.1)

- Alta rápida de partidas: torneos (re-entries, primas, premios en ticket), Sit&Go,
  Expresso (con multiplicador) y cash
- Dashboard con resultado neto, ROI e ITM por modalidad
- Resultados por día y por mes, gráfica de evolución
- Banca por sala: depósitos, retiradas, bonos y conciliación con el saldo real
- Varias monedas (EUR y USD de serie, ampliable)
- Interfaz en español e inglés
- Importación desde una hoja de cálculo existente (CSV)
- Copias de seguridad diarias de la base de datos

El roadmap completo está en las [issues](https://github.com/kete1987/poker-bankroll/issues).

## Stack

| Parte | Tecnología |
|---|---|
| Base de datos | PostgreSQL 18 |
| Backend | Java 25, Spring Boot 4.1, Flyway, OpenAPI |
| Frontend | React 19, TypeScript 7, Vite 8, Mantine 9, TanStack Query, react-i18next, ECharts |
| Despliegue | Docker Compose (imágenes en GHCR, `amd64` y `arm64`) |

```
navegador ──► web (nginx + SPA) ──/api──► api (Spring Boot) ──► db (PostgreSQL)
```

## Puesta en marcha

Necesitas Docker con Compose. La aplicación todavía es un esqueleto, pero el stack ya funciona:

```bash
git clone https://github.com/kete1987/poker-bankroll.git
cd poker-bankroll/deploy
cp .env.example .env          # pon un POSTGRES_PASSWORD
docker compose up -d --build  # construye las imágenes desde el código
```

Abre `http://<tu-host>:8080` (el puerto se cambia con `WEB_PORT` en `.env`). Los datos se guardan
en el volumen de Docker `poker-bankroll_db-data`.

En lugar de construirlas, `docker compose pull && docker compose up -d` usa las imágenes ya
publicadas en GHCR (`linux/amd64` y `linux/arm64`). Se eligen con `POKER_BANKROLL_VERSION`: `latest`
(última versión estable), una versión concreta como `0.1.0`, o `edge` (último merge a `main`).
En Portainer, crea un stack a partir de `deploy/docker-compose.yml` y define las variables de
`.env.example`. Las versiones, actualizaciones y vueltas atrás se explican en
[docs/releasing.md](docs/releasing.md) (en inglés).

> La aplicación no tiene login. Úsala en tu red local y no la expongas a internet sin
> poner autenticación delante.

## Estructura del repositorio

```
backend/    API REST con Spring Boot
frontend/   Aplicación web con React
deploy/     Stack de Docker Compose y configuración de nginx
docs/       Documentación
```

## Contribuir

Las issues y pull requests son bienvenidas. Las convenciones están en
[AGENTS.md](AGENTS.md) (en inglés; aplican también a personas).

## Licencia

[MIT](LICENSE)
