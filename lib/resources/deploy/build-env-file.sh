#!/usr/bin/env bash
# Prints the .env for a deploy: the app's own settings from the credential
# file, then the values the deploy sets. Lines in the credential file for
# those values are left out, so each value appears once.
#
# Needs ENV_FILE, IMAGE_TAG, GITHUB_USER and APP_NAME. DOMAIN is optional.
set -euo pipefail

: "${ENV_FILE:?ENV_FILE is not set}"
: "${IMAGE_TAG:?IMAGE_TAG is not set}"
: "${GITHUB_USER:?GITHUB_USER is not set}"
: "${APP_NAME:?APP_NAME is not set}"

deploy_keys="IMAGE_TAG|GITHUB_USER|APP_NAME"
if [ -n "${DOMAIN:-}" ]; then
  deploy_keys="$deploy_keys|DOMAIN"
fi

# awk ends every line it prints with a newline, so a credential file without
# a final newline cannot run into the first deploy value.
awk -v pattern="^($deploy_keys)=" '$0 !~ pattern' "$ENV_FILE"

printf 'IMAGE_TAG=%s\n' "$IMAGE_TAG"
printf 'GITHUB_USER=%s\n' "$GITHUB_USER"
printf 'APP_NAME=%s\n' "$APP_NAME"
if [ -n "${DOMAIN:-}" ]; then
  printf 'DOMAIN=%s\n' "$DOMAIN"
fi
