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

report() { # level message
  if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::$1::$2"; else echo "$1: $2" >&2; fi
}

version=${1:?usage: release-preflight.sh <version> <sha>}
sha=${2:?usage: release-preflight.sh <version> <sha>}
tag="v$version"

if ! [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
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
  # Milestones are named after the minor version they deliver, e.g. "v0.1 MVP" -> 0.1.x.
  minor="v${version%.*}"
  open=$(gh api "repos/{owner}/{repo}/milestones?state=all" \
    --jq "[.[] | select(.title | startswith(\"$minor\")) | .open_issues] | add // 0")
  if [ "$open" -gt 0 ]; then
    report warning "milestone $minor still has $open open issue(s)"
    exit 2
  fi
  echo "Milestone $minor: no open issues"
fi
