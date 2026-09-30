#!/usr/bin/env bash
# Works out the version and the image tags of a release build.
#
#   release-version.sh <ref-type> <ref-name> <sha>
#
#   tag   v1.2.3        -> version=1.2.3        tags="1.2.3 1.2 latest"  prerelease=false
#   tag   v1.2.3-rc.1   -> version=1.2.3-rc.1   tags="1.2.3-rc.1"        prerelease=true
#   branch main         -> version=edge-<sha7>  tags="edge sha-<sha7>"   prerelease=false
#
# The moving tags only move forward: "X.Y" when the version is the highest stable one of its
# series and "latest" when it is the highest stable one overall, so an older release (a fix from a
# maintenance branch, or an old release re-run) never takes them back.
# Needs the tags in the local clone (fetch-depth: 0 in the workflow).
#
# Prints key=value lines, ready to append to $GITHUB_OUTPUT.
set -euo pipefail

ref_type=$1
ref_name=$2
sha=$3

if [ "$ref_type" = "tag" ]; then
  version=${ref_name#v}
  if ! [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
    echo "::error::Tag '$ref_name' is not a version like v1.2.3 or v1.2.3-rc.1" >&2
    exit 1
  fi
  if [[ $version == *-* ]]; then
    # Pre-releases never move the moving tags.
    prerelease=true
    tags=$version
  else
    prerelease=false
    tags=$version
    series=${version%.*}
    # Highest stable version (exactly X.Y.Z, so pre-releases and malformed tags such as v9.0.0oops
    # do not count) among the existing tags matching a glob, counting this one.
    highest() {
      { git tag --list "$1" | sed 's/^v//' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' || true; echo "$version"; } | sort -V | tail -n 1
    }
    if [ "$(highest "v$series.*")" = "$version" ]; then
      tags="$tags $series"
    fi
    if [ "$(highest 'v[0-9]*')" = "$version" ]; then
      tags="$tags latest"
    fi
  fi
else
  short=${sha:0:7}
  version="edge-$short"
  tags="edge sha-$short"
  prerelease=false
fi

echo "version=$version"
echo "tags=$tags"
echo "prerelease=$prerelease"
