# Backups and restore

The Compose stack in `deploy/` includes a `backup` service that dumps the database every day to
a folder on the host and rotates the dumps. It is on by default: there is nothing to set up for
a basic installation. This page explains what is saved, how to change the schedule and
retention, how to take a backup now and how to restore one.

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

2. Choose the dump, for example the one of a given day:

   ```bash
   ls -l backups/daily backups/last
   ```

   Optionally, keep a copy of the current data first:

   ```bash
   docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges' | gzip > ~/poker-bankroll-before-restore.sql.gz
   ```

3. Drop the database and create it again, empty:

   ```bash
   docker compose exec db sh -c 'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
   ```

4. Load the dump (replace the file name with the one you chose):

   ```bash
   docker compose exec -T db sh -c 'gunzip | psql -v ON_ERROR_STOP=1 --quiet -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < backups/daily/pokerbankroll-20260930.sql.gz
   ```

   `psql` prints a `set_config` line; any error stops the load and is shown.
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

Set up the stack as in the README (`.env` with your settings), then start only the database,
restore and start the rest:

```bash
docker compose up -d --wait db
# steps 3 and 4 above, with the dump copied to this machine
docker compose up -d --wait
```

### Portainer

`docker compose` needs the Compose file, which Portainer keeps to itself. Stop and start the
`api` and `backup` containers from the Portainer UI, and run steps 3 and 4 in a shell of the
host with `docker exec` in place of `docker compose exec` and the database container name
shown by `docker ps` (e.g. `poker-bankroll-db-1`):

```bash
docker exec poker-bankroll-db-1 sh -c 'dropdb ...'           # step 3, same quoted command
docker exec -i poker-bankroll-db-1 sh -c 'gunzip | psql ...' < /srv/poker-bankroll/backups/daily/pokerbankroll-20260930.sql.gz   # step 4
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
