#!/usr/bin/env bats

bats_require_minimum_version 1.5.0

setup() {
  cd "$BATS_TEST_TMPDIR" || exit 1
  script="$BATS_TEST_DIRNAME/../resources/deploy/restore-snapshot.sh"

  # A stand-in for docker that records that it was called.
  mkdir bin
  cat >bin/docker <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >"$BATS_TEST_TMPDIR/docker-args"
STUB
  chmod +x bin/docker
  PATH="$BATS_TEST_TMPDIR/bin:$PATH"
}

@test "refuses when there is no snapshot" {
  run --separate-stderr bash "$script"

  [ "$status" -ne 0 ]
  [ ! -e docker-args ]
}

@test "puts back the snapshot's files and .env, then starts them" {
  echo "broken compose" >docker-compose.yml
  echo "IMAGE_TAG=new1234" >.env
  mkdir .rollback
  echo "good compose" >.rollback/docker-compose.yml
  echo "IMAGE_TAG=old1234" >.rollback/.env

  run bash "$script"

  [ "$status" -eq 0 ]
  [ "$(cat docker-compose.yml)" = "good compose" ]
  [ "$(cat .env)" = "IMAGE_TAG=old1234" ]
  [[ "$output" == *"old1234"* ]]
  grep -q "compose up --detach --wait" docker-args
}
