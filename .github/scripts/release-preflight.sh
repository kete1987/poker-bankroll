#!/usr/bin/env bash
# Checks whether VERSION can be released from commit SHA. Shared by scripts/release.sh and the
# "Run workflow" button of the Release workflow.
#
#   release-preflight.sh <version> <sha>
#
# Exit 0: ready. Exit 1: blocking problem (bad version, tag exists, CI not green).
# Exit 2: only warnings (the milestone of a stable release still has open issues).
# Needs git (with an "origin" remote) and an authenticated GitHub CLI (gh).
set -euo pipefail
# shellcheck source=semver.sh
source "$(dirname "$0")/semver.sh"

report() { # level message
  if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::$1::$2"; else echo "$1: $2" >&2; fi
}

version=${1:?usage: release-preflight.sh <version> <sha>}
sha=${2:?usage: release-preflight.sh <version> <sha>}
tag="v$version"

if ! is_version "$version"; then
  report error "'$version' is not a version like 1.2.3 or 1.2.3-rc.1"
  exit 1
fi

if git ls-remote --exit-code --tags origin "refs/tags/$tag" > /dev/null; then
  report error "tag $tag already exists"
  exit 1
fi

ci=$(gh run list --workflow ci.yml --commit "$sha" --json conclusion,status \
  --jq 'if length == 0 then "missing" elif .[0].status != "completed" then "running" else .[0].conclusion end')
if [ "$ci" != success ]; then
  report error "CI for ${sha:0:7} is '$ci'; a release needs it green (wait for it or fix it first)"
  exit 1
fi
echo "CI for ${sha:0:7}: success"

if [[ $version != *-* ]]; then
  # Milestones are named after the minor version they deliver: "v0.1" or "v0.1 <name>" -> 0.1.x.
  # Match the whole minor ("v0.1" must not match "v0.10") and require exactly one milestone, so a
  # mistyped version (no milestone) is not taken as "nothing left to do".
  minor="v${version%.*}"
  pattern="^${minor//./\\\\.}( |$)"
  milestones=$(gh api --paginate "repos/{owner}/{repo}/milestones?state=all&per_page=100" \
    --jq ".[] | select(.title | test(\"$pattern\")) | \"\(.open_issues) \(.title)\"")
  count=$(printf '%s' "$milestones" | grep -c . || true)
  if [ "$count" -eq 0 ]; then
    report warning "there is no milestone for $minor: check the version is the intended one"
    exit 2
  fi
  if [ "$count" -gt 1 ]; then
    report warning "several milestones match $minor: $(printf '%s' "$milestones" | cut -d' ' -f2- | paste -sd ',' -)"
    exit 2
  fi
  open=${milestones%% *}
  title=${milestones#* }
  if [ "$open" -gt 0 ]; then
    report warning "milestone '$title' still has $open open issue(s)"
    exit 2
  fi
  echo "Milestone '$title': no open issues"
fi
