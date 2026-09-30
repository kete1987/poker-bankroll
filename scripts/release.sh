#!/usr/bin/env bash
# Cuts a release: checks that main is ready, then creates and pushes the vX.Y.Z tag.
# GitHub Actions (.github/workflows/release.yml) builds and publishes the images and the release.
#
#   scripts/release.sh 0.1.0         # stable release: images 0.1.0, 0.1 and latest
#   scripts/release.sh 0.2.0-rc.1    # pre-release: image 0.2.0-rc.1 only
#
# Needs git and, for the CI and milestone checks, the GitHub CLI (gh). See docs/releasing.md.
set -euo pipefail

fail() { echo "error: $*" >&2; exit 1; }
warn() { echo "warning: $*" >&2; }

version=${1:-}
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] \
  || fail "usage: $0 X.Y.Z[-pre] (e.g. 0.1.0 or 0.2.0-rc.1)"
tag="v$version"
prerelease=false
[[ $version == *-* ]] && prerelease=true

cd "$(git rev-parse --show-toplevel)"
git fetch --quiet --tags origin

[ "$(git rev-parse --abbrev-ref HEAD)" = main ] || fail "releases are cut from main; run: git switch main"
git diff --quiet && git diff --cached --quiet || fail "there are uncommitted changes"
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] \
  || fail "local main is not origin/main; run: git pull"
! git rev-parse -q --verify "refs/tags/$tag" > /dev/null || fail "tag $tag already exists"

commit=$(git rev-parse HEAD)
problems=0

if command -v gh > /dev/null; then
  ci=$(gh run list --workflow ci.yml --commit "$commit" --json conclusion,status \
    --jq 'if length == 0 then "missing" elif .[0].status != "completed" then "running" else .[0].conclusion end')
  if [ "$ci" != success ]; then
    warn "CI for ${commit:0:7} is '$ci' (expected 'success')"
    problems=1
  fi

  if [ "$prerelease" = false ]; then
    # Milestones are named after the minor version they deliver, e.g. "v0.1 MVP" -> 0.1.x.
    minor="v${version%.*}"
    open=$(gh api "repos/{owner}/{repo}/milestones?state=all" \
      --jq "[.[] | select(.title | startswith(\"$minor\")) | .open_issues] | add // 0")
    if [ "$open" -gt 0 ]; then
      warn "milestone $minor still has $open open issue(s)"
      problems=1
    fi
  fi
else
  warn "gh not found: CI and milestone checks skipped"
fi

echo
echo "About to release $tag from ${commit:0:7}: $(git log -1 --format=%s)"
[ "$problems" -eq 0 ] || echo "(see the warnings above)"
read -r -p "Create and push tag $tag? [y/N] " answer
[[ $answer =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }

git tag -a "$tag" -m "Release $tag"
git push origin "$tag"

echo
echo "Tag pushed. Follow the build with:  gh run watch \$(gh run list --workflow release.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
