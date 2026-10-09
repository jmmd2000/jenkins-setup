#!/usr/bin/env bats

bats_require_minimum_version 1.5.0

setup() {
  ENV_FILE="$BATS_TEST_TMPDIR/credential.env"
  export ENV_FILE
  export IMAGE_TAG="abc1234"
  export GITHUB_USER="github-user"
  export APP_NAME="album-reviews-staging"
  export DOMAIN="staging.example.com"
}

@test "keeps the app settings and adds the deploy values" {
  printf 'POSTGRES_USER=admin\nJWT_SECRET=secret\n' >"$ENV_FILE"

  run bash lib/resources/deploy/build-env-file.sh

  [ "$status" -eq 0 ]
  [ "$output" = "POSTGRES_USER=admin
JWT_SECRET=secret
IMAGE_TAG=abc1234
GITHUB_USER=github-user
APP_NAME=album-reviews-staging
DOMAIN=staging.example.com" ]
}

@test "replaces deploy values that the credential file already has" {
  printf 'IMAGE_TAG=old1234\nPOSTGRES_USER=admin\nAPP_NAME=old-name\n' >"$ENV_FILE"

  run bash lib/resources/deploy/build-env-file.sh

  [ "$status" -eq 0 ]
  [ "$(grep -c '^IMAGE_TAG=' <<<"$output")" -eq 1 ]
  [ "$(grep -c '^APP_NAME=' <<<"$output")" -eq 1 ]
  [[ "$output" == *"IMAGE_TAG=abc1234"* ]]
  [[ "$output" != *"old"* ]]
}

@test "starts the deploy values on a new line when the credential file has no final newline" {
  printf 'POSTGRES_USER=admin' >"$ENV_FILE"

  run bash lib/resources/deploy/build-env-file.sh

  [ "${lines[0]}" = "POSTGRES_USER=admin" ]
  [ "${lines[1]}" = "IMAGE_TAG=abc1234" ]
}

@test "keeps the credential file's DOMAIN when the environment has no domain" {
  printf 'DOMAIN=from-credential.example.com\n' >"$ENV_FILE"
  DOMAIN=""

  run bash lib/resources/deploy/build-env-file.sh

  [ "$status" -eq 0 ]
  [ "$(grep -c '^DOMAIN=' <<<"$output")" -eq 1 ]
  [[ "$output" == *"DOMAIN=from-credential.example.com"* ]]
}

@test "fails without printing anything when a deploy value is missing" {
  printf 'POSTGRES_USER=admin\n' >"$ENV_FILE"
  unset IMAGE_TAG

  run --separate-stderr bash lib/resources/deploy/build-env-file.sh

  [ "$status" -ne 0 ]
  [ "$output" = "" ]
}
