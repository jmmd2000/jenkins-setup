#!/usr/bin/env bash
# Saves the deploy folder's files and .env as the last good deploy, in .rollback.
# Run it in the deploy folder after a successful deploy.
#
# Needs FILES: the file names to save, separated by commas.
set -euo pipefail

: "${FILES:?FILES is not set}"

# The snapshot holds .env, so only the deploy user can read it.
umask 077

IFS="," read -r -a file_names <<<"$FILES"

# A new snapshot is built next to the old one and only replaces it when it is
# complete, so a failed copy keeps the last good snapshot.
rm -rf .rollback.new
mkdir .rollback.new
for name in "${file_names[@]}" .env; do
  cp -p "$name" ".rollback.new/$name"
done
rm -rf .rollback
mv .rollback.new .rollback

echo "Saved this deploy for rollback."
