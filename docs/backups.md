# Backups and restore

There are two kinds of backup, and they complement each other:

| | [Backup from the app](#backup-from-the-app) | [Automatic backups](#automatic-backups) |
|---|---|---|
| What it is | One JSON file with your data, downloaded from *Import / Export* | A `pg_dump` of the whole database, made by the `backup` service of the stack |
| Made | When you ask for it | Every day, on its own, with daily, weekly and monthly retention |
| Restored | From the app, with two clicks | With commands, in a shell on the server |
| Restored into | Any installation of the same or a newer version, also on another machine | The same database server |
| Use it to | Move to another computer; keep a copy on your own machine; go back after a mistake | Recover from a broken disk or database without having thought of it beforehand |

Keep the automatic backups on: they are the ones that exist when something goes wrong and nobody
downloaded anything. Use the backup from the app when **you** want a copy, and above all to move
the installation somewhere else: no shell is needed on either side.

## Backup from the app

In the app, open **Import / Export** and go to *Backup of everything*.

- **Download backup** saves one file, `poker-bankroll-backup-<date>.json`, with everything you
  created: rooms and their logos, your own variants and which built-in ones are active, every
  game (those in play too) with its tags, and every bankroll movement. It is not encrypted: it holds all your
  data, so keep it where only you can read it.
- **Choose backup file** takes such a file and only checks it: the page shows what it holds
  (rooms, variants, games, movements, dates) next to what the installation holds now. Nothing
  changes yet.
- **Restore this backup** replaces **everything** the installation holds with the content of the
  file. When the installation has data, the page says in red what will be deleted, and the
  confirmation only goes on after ticking a box. There is no undo: download a backup of the
  current data first if you may need it.

It is all or nothing: if the file has an error, or anything fails halfway, the installation is
left exactly as it was, and the page lists what is wrong and where in the file.

### Move to another computer

1. On the old installation: *Import / Export* → **Download backup**.
2. Set up the new installation as in the README and open it: it is empty.
3. On the new one: *Import / Export* → **Choose backup file** → **Restore this backup**.

The new installation can be a newer version than the old one, not an older one: the file says
the version of its format, every version of the app reads the formats of the previous ones, and
a file made by a newer version is refused with a message saying so (update the app, then
restore).

### What the file is

A JSON document, readable with any text editor:

```json
{
  "formatVersion": 1,
  "appVersion": "0.2.0",
  "exportedAt": "2026-10-02T10:15:30Z",
  "rooms": [
    {"id": 1, "name": "Winamax", "currencyCode": "EUR", "active": true,
     "logo": {"contentType": "image/png", "content": "iVBORw0KGgo..."}}
  ],
  "variants": [
    {"id": 2, "gameType": "TOURNAMENT", "code": "KO", "active": true},
    {"id": 13, "gameType": "SIT_AND_GO", "name": "Hyper Turbo 6-max", "active": true}
  ],
  "games": [
    {"playedOn": "2026-01-19", "playedAt": "21:30:00", "roomId": 1, "gameType": "TOURNAMENT",
     "modality": "NLHE", "variantId": 2, "status": "FINISHED", "name": "Kill The Fish",
     "buyIn": 10.00, "entries": 2, "prize": 80.50, "bounty": 12.25, "ticketPrizeValue": 0.00,
     "paidWithTicket": false, "notes": "Final table", "tags": ["Challenge", "Satellite"]}
  ],
  "movements": [
    {"occurredOn": "2026-01-01", "type": "DEPOSIT", "roomId": 1, "amount": 500.00},
    {"occurredOn": "2026-01-01", "type": "DEPOSIT", "currencyCode": "EUR", "amount": 1000.00}
  ]
}
```

- The `id` of a room or a variant only means something inside the file: games and movements name
  them by it. The ids of the database, and the dates the rows were created, are not kept.
- Built-in variants (those with a `code`) are only there to say whether they are active; the
  ones with a `name` are yours.
- Each game names its tags (`tags`, left out when it has none), and restoring creates them: a tag
  that no game has is not kept. Files made before tags existed restore with games without tags.
- Currencies are named by their code. A file with a currency the installation does not have is
  an error.
- The file can be up to 32 MB, which is more than 100,000 games with their notes and dozens of
  logos.

The API behind it is `GET /api/backup` and `POST /api/backup/restore?dryRun=&replace=` (see the
Swagger UI), so a backup can also be scheduled with `curl` from another machine:

```bash
curl -fsS -o "poker-bankroll-$(date +%F).json" http://your-server:8080/api/backup
```

## Automatic backups

The Compose stack in `deploy/` includes a `backup` service that dumps the database every day to
a folder on the host and rotates the dumps. It is on by default: there is nothing to set up for
a basic installation. The rest of this page explains what is saved, how to change the schedule
and retention, how to take a backup now and how to restore one.

All commands are run from the `deploy/` folder (where `docker-compose.yml` and `.env` are). See
[Portainer](#portainer) if you run the stack without a shell there.

## What is backed up

All the data of the app: the whole application database (`POSTGRES_DB`) with its schema.
Each backup is a gzip-compressed SQL file made with `pg_dump` and restored with `psql`. Dumps
contain no owner or privilege statements, so they can be restored under any database user name.

Not included: your `.env` file. It is not needed to restore a dump, but keep a copy of it to
recreate the stack with the same settings.

The service uses the [`prodrigestivill/postgres-backup-local`](https://github.com/prodrigestivill/docker-postgres-backup-local)
image, pinned to a PostgreSQL 18 build to match the `db` service.

## Where

Dumps are written to `BACKUP_DIR` on the host, by default `./backups` next to
`docker-compose.yml` (`deploy/backups/`, ignored by git). Docker creates the folder on first
start.

| Path | Content |
|---|---|
| `last/pokerbankroll-YYYYMMDD-HHMMSS.sql.gz` | Every dump; kept for 24 hours |
| `daily/pokerbankroll-YYYYMMDD.sql.gz` | Latest dump of each day |
| `weekly/pokerbankroll-YYYYWW.sql.gz` | Latest dump of each ISO week |
| `monthly/pokerbankroll-YYYYMM.sql.gz` | Latest dump of each month |
| `*/pokerbankroll-latest.sql.gz` | Symbolic link to the newest file of that folder |

(`pokerbankroll` is the database name, `POSTGRES_DB`.) The daily, weekly and monthly files are
hard links to the dump in `last/`, so they take no extra space. For that reason `BACKUP_DIR`
must be on a filesystem with hard and symbolic links (ext4, XFS, btrfs, ZFS, NFS...), not on a
FAT/exFAT drive or an SMB/CIFS share: keep the dumps on a local disk and
[copy them](#copy-backups-off-the-machine) to other places.

**Portainer:** set `BACKUP_DIR` to an absolute host path, such as `/srv/poker-bankroll/backups`.
A relative path is resolved inside Portainer's own data folder, not next to your files.

### File permissions

The container runs as `root` (`BACKUP_USER=0:0`) so that it can write to the folder, including
one that Docker creates (owned by `root`). The dumps are then owned by `root` and, by default,
readable by every user of the host. They contain all your data, so restrict the folder if other
people use the machine:

```bash
sudo chmod 700 backups
```

To have the files owned by your own user instead, create the folder with that owner and set
`BACKUP_USER` to its user and group ids (`id -u` and `id -g`), then run `docker compose up -d`:

```bash
mkdir -p /srv/poker-bankroll/backups && chmod 700 /srv/poker-bankroll/backups
# .env: BACKUP_DIR=/srv/poker-bankroll/backups and BACKUP_USER=1000:1000
```

If the container user cannot write to the folder, the `backup` service logs
`BACKUP_DIR points to a file or folder with insufficient permissions.` and keeps restarting.

## Schedule and retention

A dump is taken when the service starts and then on `BACKUP_SCHEDULE`. Set these variables in
`.env` (or in the Portainer stack) and apply them with `docker compose up -d`:

| Variable | Default | Meaning |
|---|---|---|
| `BACKUP_DIR` | `./backups` | Host folder for the dumps |
| `BACKUP_SCHEDULE` | `@daily` | `@daily` (midnight), `@every 6h`, or a cron expression such as `30 3 * * *` (every day at 03:30) |
| `BACKUP_TZ` | `UTC` | Time zone of the schedule and of the file names, e.g. `Europe/Madrid` |
| `BACKUP_KEEP_DAYS` | `7` | Daily dumps to keep |
| `BACKUP_KEEP_WEEKS` | `4` | Weekly dumps to keep |
| `BACKUP_KEEP_MONTHS` | `6` | Monthly dumps to keep |
| `BACKUP_USER` | `0:0` | `user:group` the container runs as (see [File permissions](#file-permissions)) |

With the defaults you can go back to any of the last 7 days, the last 4 weeks and the last
6 months. Old files are removed only after a successful backup, so a failing backup never
deletes the previous ones.

The service is `healthy` while the last automatic backup (on start or scheduled) succeeded and
becomes `unhealthy` when one fails (see `docker compose ps` or Portainer). The details are in `docker compose logs backup`.

The service can be stopped with `docker compose stop backup`, but running without backups is
not recommended.

## Take a backup now

```bash
docker compose exec backup /backup.sh
```

The new dump appears in `last/` and replaces today's file in `daily/`, `weekly/` and `monthly/`.
Do this before risky operations such as an upgrade.

> In Git Bash on Windows, prefix the command with `MSYS_NO_PATHCONV=1` so that `/backup.sh` is
> not turned into a Windows path.

## Restore

Restoring **replaces all current data** with the content of the dump.

1. Stop the API and the backup service. The web page shows errors until step 5; no new dump
   replaces the file you are about to restore.

   ```bash
   docker compose stop api backup
   ```

2. Choose the dump, for example the one of a given day, and **check that it is intact** before
   touching the database:

   ```bash
   ls -l backups/daily backups/last
   docker compose exec -T db gunzip -t < backups/daily/pokerbankroll-20260930.sql.gz && echo "dump OK"
   ```

   If it does not print `dump OK` (e.g. `unexpected end of file` or `crc error`), the file is
   truncated or damaged: **do not go on**, pick another dump.

   Optionally, keep a copy of the current data first:

   ```bash
   docker compose exec -T db sh -c 'set -o pipefail; pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges | gzip' > ~/poker-bankroll-before-restore.sql.gz && echo "copy OK"
   ```

   Only rely on this copy if it prints `copy OK`: dump and compression run inside the container
   with `pipefail`, so a failing `pg_dump` is reported instead of leaving an empty file.

3. Drop the database and create it again, empty (from here on the current data is gone):

   ```bash
   docker compose exec db sh -c 'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
   ```

4. Load the dump (replace the file name with the one you chose):

   ```bash
   docker compose exec -T db sh -c 'set -o pipefail; gunzip | psql -v ON_ERROR_STOP=1 --quiet -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < backups/daily/pokerbankroll-20260930.sql.gz && echo "restore OK"
   ```

   `psql` prints a `set_config` line. It must end with `restore OK`: any error in the SQL
   (`ON_ERROR_STOP`) or in the decompression (`pipefail`) stops it and is shown. Without
   `pipefail` a damaged file could load partially and still look successful. If it fails,
   repeat steps 3 and 4 with another dump, or with the copy of the current data from step 2.
   If your user cannot read the file (see [File permissions](#file-permissions)), feed it with
   `sudo cat <file> | docker compose exec -T db sh -c '...'` instead of `< <file>`.

5. Start everything again and check that the API is up:

   ```bash
   docker compose up -d --wait
   curl http://localhost:8080/api/actuator/health   # {"status":"UP"...}; use your WEB_PORT
   ```

   On start the backup service takes a fresh dump of the restored data.

A dump made by an older version of poker-bankroll can be restored into a newer one: the API
applies the missing database migrations when it starts. The opposite does not work; restore
into the same or a newer version.

### Restore on a new machine

The easy way to move to another machine is the [backup from the app](#move-to-another-computer).
With a dump instead: set up the stack as in the README (`.env` with your settings), then start only the database,
restore and start the rest:

```bash
docker compose up -d --wait db
# steps 2 (check the dump), 3 and 4 above, with the dump copied to this machine
docker compose up -d --wait
```

### Portainer

`docker compose` needs the Compose file, which Portainer keeps to itself. Stop and start the
`api` and `backup` containers from the Portainer UI, and run steps 3 and 4 in a shell of the
host with `docker exec` in place of `docker compose exec` and the database container name
shown by `docker ps` (e.g. `poker-bankroll-db-1`):

```bash
docker exec -i poker-bankroll-db-1 gunzip -t < /srv/poker-bankroll/backups/daily/pokerbankroll-20260930.sql.gz && echo "dump OK"   # step 2
docker exec poker-bankroll-db-1 sh -c 'dropdb ...'           # step 3, same quoted command
docker exec -i poker-bankroll-db-1 sh -c 'set -o pipefail; gunzip | psql ...' < /srv/poker-bankroll/backups/daily/pokerbankroll-20260930.sql.gz && echo "restore OK"   # step 4
```

A backup can be taken from the console of the backup container in Portainer by running
`/backup.sh`.

## Copy backups off the machine

A backup on the same disk as the database does not survive a disk failure or a lost machine.
Copy `BACKUP_DIR` somewhere else regularly, for example to a NAS with `rsync` from a cron job
of the host that runs after the backup:

```bash
rsync -aH --delete /path/to/poker-bankroll/deploy/backups/ nas:/backups/poker-bankroll/
```

`-H` keeps the hard links (otherwise every file is copied up to four times) and `--delete`
mirrors the local retention; drop it to keep the whole history on the other side. To copy only
the newest dump, for example to a USB drive, follow the link with `cp -L`:

```bash
cp -L backups/daily/pokerbankroll-latest.sql.gz /media/usb/
```

Dumps are not encrypted. Encrypt them before storing them with a third party (for example
with `gpg --symmetric` or an `rclone` crypt remote).
