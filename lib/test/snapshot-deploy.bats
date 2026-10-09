#!/usr/bin/env bats

bats_require_minimum_version 1.5.0

setup() {
  cd "$BATS_TEST_TMPDIR" || exit 1
  echo "services: {}" >docker-compose.yml
  echo "echo backup" >backup.sh
  chmod 755 backup.sh
  echo "IMAGE_TAG=abc1234" >.env
  chmod 600 .env
  export FILES="docker-compose.yml,backup.sh"
  script="$BATS_TEST_DIRNAME/../resources/deploy/snapshot-deploy.sh"
}

@test "saves the listed files and .env, keeping their modes" {
  run bash "$script"

  [ "$status" -eq 0 ]
  [ "$(cat .rollback/.env)" = "IMAGE_TAG=abc1234" ]
  [ -f .rollback/docker-compose.yml ]
  [ "$(stat -c %a .rollback/.env)" = "600" ]
  [ "$(stat -c %a .rollback/backup.sh)" = "755" ]
  [ "$(stat -c %a .rollback)" = "700" ]
}

@test "replaces the whole old snapshot" {
  mkdir .rollback
  echo "old" >.rollback/old-file.yml

  run bash "$script"

  [ "$status" -eq 0 ]
  [ ! -e .rollback/old-file.yml ]
}

@test "keeps the old snapshot when a file is missing" {
  mkdir .rollback
  echo "IMAGE_TAG=good999" >.rollback/.env
  export FILES="docker-compose.yml,missing.sh"

  run --separate-stderr bash "$script"

  [ "$status" -ne 0 ]
  [ "$(cat .rollback/.env)" = "IMAGE_TAG=good999" ]
}
