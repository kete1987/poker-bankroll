# Releasing and deploying

## Versions

Versions follow [semantic versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.

- While the major version is `0`, a new **minor** (`0.2.0`) brings new features and a new
  **patch** (`0.1.1`) only fixes. `1.0.0` marks the first version considered stable.
- A **pre-release** (`0.2.0-rc.1`, `0.2.0-rc.2`...) is a candidate to try before the real release.
- Each **milestone** delivers one minor version: milestone `v0.1 MVP` becomes release `0.1.0`,
  `v0.2` becomes `0.2.0`. Fixes after a release are patch releases of the same minor.

The git tag is the only place where the version is set: the release build passes it to Maven
(`-Drevision`), to the frontend (`APP_VERSION`) and to the image labels. The app shows it in the
header, and the API reports it in `/api/actuator/info` and in the OpenAPI document.
Local builds say `0.1.0-SNAPSHOT` (API) and `dev` (web).

## Images

Every build publishes `ghcr.io/kete1987/poker-bankroll-api` and `ghcr.io/kete1987/poker-bankroll-web`
for `linux/amd64` and `linux/arm64`, always with the same tags:

| Tag | Published when | Use it to |
|---|---|---|
| `0.1.0` | Release `v0.1.0`; never changes afterwards | Pin an exact version, roll back |
| `0.1` | Every release of the 0.1 series | Get fixes but no new features |
| `latest` | Every **stable** release | Always run the latest stable version |
| `0.2.0-rc.1` | Pre-release `v0.2.0-rc.1` | Try a release candidate |
| `edge` | Every merge to `main` | Try what was just merged, before a release |
| `sha-<commit>` | Merges to `main` (best effort, see below) | Pin a specific `main` build |

Pre-releases never move `0.X` or `latest`.

`edge` builds of `main` supersede each other: when a new merge arrives while the previous build is
still running, the older one is cancelled. `edge` always ends up on the latest merge, but a
`sha-<commit>` image may be missing for a commit that was followed quickly by another one. Use
versions, not `sha-*`, for anything you need to keep.

## Flow

1. **Work** happens in issues and pull requests. Every merge to `main` publishes `edge`
   (workflow `Release`). Commits only reach `main` with CI green.
2. **Try it**: point a test stack at `POKER_BANKROLL_VERSION=edge` and pull.
3. **Release candidate** (optional): cut `0.2.0-rc.1` (see *How to cut a release* below). It creates
   a GitHub *pre-release* and the `0.2.0-rc.1` images, without touching `latest`. Fix what is found
   through normal PRs and cut `rc.2` if needed. Worth it before a minor release (new features);
   patch releases usually go straight to the release.
4. **Release**: when the milestone is done, cut `0.2.0`. GitHub Actions publishes the images
   (`0.2.0`, `0.2`, `latest`) and creates the GitHub Release with notes generated from the merged
   PRs, grouped by their labels.
5. **Close the milestone** in GitHub.

```
merges to main ──► edge (published automatically)
                     │
       release candidate? ── yes ──► v0.2.0-rc.1 ──► try it ──► fix via PR ──► v0.2.0-rc.2 ...
                     │                                  │
                     no                                 ok
                     ▼                                  ▼
                  v0.2.0  (0.2.0, 0.2, latest + GitHub Release)
```

## How to cut a release

All three ways end in the same `Release` workflow run, which builds and publishes everything.
The first two run the same checks (`.github/scripts/release-preflight.sh`): valid version, tag not
used yet, **CI green** for the commit (blocking) and, for stable releases, that **exactly one
milestone** matches the minor version (`v0.1` or `v0.1 <name>`) and it has **no open issues**
(a warning you must confirm).

- **From GitHub** (no local setup, works from a phone): *Actions → Release → Run workflow*, keep the
  branch on `main`, type the version (`0.2.0` or `0.2.0-rc.1`, without `v`). Tick
  *ignore-milestone* only to release on purpose when the milestone is missing or still has open
  issues. The run checks, creates the
  tag on the current `main` commit and publishes.
- **From your machine**: on an up-to-date, clean `main`, run `scripts/release.sh 0.2.0`. It checks,
  asks for confirmation and pushes the tag; the push starts the workflow.
- **From the Releases page** (*Draft a new release* with a new `vX.Y.Z` tag on `main`): the workflow
  publishes the images and keeps the release you wrote. This way skips the checks.

Whatever the way, the workflow refuses a pushed tag whose commit is not on `main` or a `release/*`
branch, so a tag on a feature branch never becomes a published version. If that happens, **delete the
rejected tag** (`git push --delete origin vX.Y.Z`): while it exists it counts as the highest
version, and later releases would not move `X.Y` or `latest`.

Follow a build in the *Actions* tab or with `gh run watch`.

If a release run fails half-way, re-run it (*Re-run failed jobs*): images that were already
published for that version are left as they are, only the missing ones are built. A published
`X.Y.Z` is never rebuilt, so rolling back to it always gives the same bits.

## Maintenance branches

Releases are cut from `main` (trunk-based): there are no long-lived release branches. A
maintenance branch is only needed when an already released version needs a fix **and** `main`
already contains unreleased changes that must not ship yet. Then:

1. Fix the bug on `main` first, through a normal PR.
2. Create the branch from the release tag, once per series: `git switch -c release/0.1 v0.1.0`
   and push it. Protect it like `main` in the branch rules if it will live long.
3. Bring the fix: `git cherry-pick -x <commit>` in a PR against `release/0.1` (CI runs there too).
4. Tag it from that branch: `git tag -a v0.1.1 -m "Release v0.1.1"` on `release/0.1` and
   `git push origin v0.1.1`. The tag starts the same `Release` workflow, which publishes `0.1.1`
   and moves `0.1`; `latest` only moves when the version is the highest released one.

`scripts/release.sh` and the *Run workflow* button only release from `main`; extend them to
`release/*` branches the first time one is needed. If `main` has nothing unreleased, just release
the fix from `main` as `0.1.1` and skip all of this.

## Deploying and updating (Docker Compose / Portainer)

- Choose what to run with `POKER_BANKROLL_VERSION` (`.env` or the Portainer stack variables):
  `latest` to follow stable releases, an exact version such as `0.1.0` to decide when to update,
  or `edge` for a test stack.
- **Update**: `docker compose pull && docker compose up -d`, or in Portainer *Pull and redeploy*.
  Only the layers that changed are downloaded.
- **Roll back**: set the previous version and redeploy. Database migrations only go forward: if the
  newer version already migrated the database, the older one may refuse to start. In that case
  restore the backup taken before the update (see the backups documentation).

## First release checklist

The first push creates the packages in GHCR. Check once that both packages
(`poker-bankroll-api`, `poker-bankroll-web`) are **public** and linked to the repository
(*Profile → Packages → package → Package settings*); otherwise pulls need a login.
