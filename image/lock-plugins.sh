#!/usr/bin/env bash
# Resolves plugins.txt and every plugin it depends on against the Jenkins
# version in the Dockerfile, then writes the exact list to plugins-lock.txt.
set -euo pipefail

image_folder="$(cd "$(dirname "$0")" && pwd)"
base_image="$(awk '/^FROM/ { print $2 }' "$image_folder/Dockerfile")"

docker run --rm --volume "$image_folder:/image" --entrypoint jenkins-plugin-cli "$base_image" \
  --plugin-file /image/plugins.txt \
  --plugin-download-directory /tmp/plugins \
  --list |
  sed -n '/^Resulting plugin list:/,$p' |
  awk 'NF == 2 { print $1 ":" $2 }' |
  sort >"$image_folder/plugins-lock.txt"

echo "Wrote $(wc -l <"$image_folder/plugins-lock.txt") plugins to plugins-lock.txt"
