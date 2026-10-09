#!/usr/bin/env bash
# Puts back the files and .env of the last good deploy, then starts it.
# Run it in the deploy folder.
set -euo pipefail

if [ ! -d .rollback ]; then
  echo "There is no snapshot of a good deploy to roll back to." >&2
  exit 1
fi

cp -a .rollback/. .
tag="$(grep '^IMAGE_TAG=' .env | tail -n 1 | cut -d= -f2)"
echo "Restored the files of the last good deploy, $tag."

docker compose up --detach --wait --wait-timeout 300 --remove-orphans
