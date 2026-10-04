#!/usr/bin/env bash
# Makes the zip attached to a GitHub Release: what someone needs to run that version, so that
# nothing has to be copied by hand (docs/install.md).
#
#   release-bundle.sh <version> [<output-dir>]
#
#   release-bundle.sh 0.2.0 dist   -> dist/poker-bankroll-0.2.0.zip
#
# The zip holds one folder, poker-bankroll/, with
#   - docker-compose.yml  (deploy/docker-compose.yml as it is)
#   - .env                (deploy/.env.example with POKER_BANKROLL_VERSION set to <version>,
#                          so the folder runs the version it was downloaded with)
# Prints the path of the zip.
set -euo pipefail

version=${1:?usage: release-bundle.sh <version> [<output-dir>]}
out_dir=${2:-.}

root=$(cd "$(dirname "$0")/../.." && pwd)
mkdir -p "$out_dir"
out_dir=$(cd "$out_dir" && pwd)
zip_file="$out_dir/poker-bankroll-$version.zip"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
folder="$work/poker-bankroll"
mkdir "$folder"

cp "$root/deploy/docker-compose.yml" "$folder/docker-compose.yml"
sed "s/^POKER_BANKROLL_VERSION=.*/POKER_BANKROLL_VERSION=$version/" \
  "$root/deploy/.env.example" > "$folder/.env"
# Fail if .env.example no longer has the line to replace.
if ! grep -qx "POKER_BANKROLL_VERSION=$version" "$folder/.env"; then
  echo "::error::deploy/.env.example has no POKER_BANKROLL_VERSION= line to set" >&2
  exit 1
fi

rm -f "$zip_file"
# -X: no extra file attributes (owner ids...) that mean nothing on the computer it is unzipped on.
(cd "$work" && zip -q -X -r "$zip_file" poker-bankroll)
echo "$zip_file"
