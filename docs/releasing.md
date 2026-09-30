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
| `sha-<commit>` | Every merge to `main` | Pin a specific `main` build |

Pre-releases never move `0.X` or `latest`.

## Flow

1. **Work** happens in issues and pull requests. Every merge to `main` publishes `edge`
   (workflow `Release`). Commits only reach `main` with CI green.
2. **Try it**: point a test stack at `POKER_BANKROLL_VERSION=edge` and pull.
3. **Release candidate** (optional, recommended before a minor release): from an up-to-date `main`,
   run `scripts/release.sh 0.2.0-rc.1`. It creates a GitHub *pre-release* and the `0.2.0-rc.1`
   images. Fix what is found through normal PRs and cut `rc.2` if needed.
4. **Release**: when the milestone is done, run `scripts/release.sh 0.2.0`. The script checks
   that you are on an up-to-date, clean `main`, that CI passed for that commit and that the
   milestone has no open issues (it warns and asks before going on), then pushes the tag.
   GitHub Actions publishes the images (`0.2.0`, `0.2`, `latest`) and creates the GitHub Release
   with notes generated from the merged PRs, grouped by their `type:*` labels.
5. **Close the milestone** in GitHub.

A release can also be created from the GitHub UI (*Releases → Draft a new release* with a new
`vX.Y.Z` tag on `main`): the workflow publishes the images and leaves that release as it is.

Follow a build with `gh run watch` or in the *Actions* tab.

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
