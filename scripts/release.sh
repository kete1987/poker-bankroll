#!/usr/bin/env bash
# Cuts a release from your machine: checks that main is ready, then creates and pushes the
# vX.Y.Z tag. GitHub Actions (.github/workflows/release.yml) builds and publishes the images and
# the GitHub Release. The same can be done from GitHub: Actions -> Release -> Run workflow.
#
#   scripts/release.sh 0.1.0         # stable release: images 0.1.0, 0.1 and latest
#   scripts/release.sh 0.2.0-rc.1    # pre-release: image 0.2.0-rc.1 only
#
# Needs git and an authenticated GitHub CLI (gh). See docs/releasing.md.
set -euo pipefail

fail() { echo "error: $*" >&2; exit 1; }

version=${1:-}
[ -n "$version" ] || fail "usage: $0 X.Y.Z[-pre] (e.g. 0.1.0 or 0.2.0-rc.1)"
tag="v$version"

cd "$(git rev-parse --show-toplevel)"
command -v gh > /dev/null || fail "the GitHub CLI (gh) is required"
git fetch --quiet --tags origin

[ "$(git rev-parse --abbrev-ref HEAD)" = main ] || fail "releases are cut from main; run: git switch main"
git diff --quiet && git diff --cached --quiet || fail "there are uncommitted changes"
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] \
  || fail "local main is not origin/main; run: git pull"

commit=$(git rev-parse HEAD)
status=0
.github/scripts/release-preflight.sh "$version" "$commit" || status=$?
[ "$status" -ne 1 ] || exit 1

echo
echo "About to release $tag from ${commit:0:7}: $(git log -1 --format=%s)"
[ "$status" -eq 0 ] || echo "(see the warning above)"
read -r -p "Create and push tag $tag? [y/N] " answer
[[ $answer =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }

git tag -a "$tag" -m "Release $tag"
git push origin "$tag"

echo
echo "Tag pushed. Follow the build with:  gh run watch \$(gh run list --workflow release.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
