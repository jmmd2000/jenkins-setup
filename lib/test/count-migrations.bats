#!/usr/bin/env bats

bats_require_minimum_version 1.5.0

# The tests run the script from standard input, the way it runs on the VPS.

setup() {
  export DATABASE_SERVICE="db"

  # A stand-in for docker that answers the two queries the script sends. Like
  # the real docker compose exec, it reads all of standard input.
  stub_dir="$BATS_TEST_TMPDIR/bin"
  mkdir -p "$stub_dir"
  cat >"$stub_dir/docker" <<'STUB'
#!/usr/bin/env bash
cat >/dev/null
if [ -n "${DOCKER_FAILS:-}" ]; then
  exit 1
fi
sql="${*: -1}"
if [[ "$sql" == *"to_regclass"* ]]; then
  echo "${TABLE_EXISTS:-t}"
else
  echo "${MIGRATION_COUNT:-0}"
fi
STUB
  chmod +x "$stub_dir/docker"
  PATH="$stub_dir:$PATH"
}

@test "prints the number of applied migrations" {
  run env MIGRATION_COUNT=6 bash -s <lib/resources/deploy/count-migrations.sh

  [ "$status" -eq 0 ]
  [ "$output" = "6" ]
}

@test "prints 0 when the migrations table does not exist yet" {
  run env TABLE_EXISTS=f bash -s <lib/resources/deploy/count-migrations.sh

  [ "$status" -eq 0 ]
  [ "$output" = "0" ]
}

@test "fails without printing a count when the database cannot be asked" {
  run --separate-stderr env DOCKER_FAILS=1 bash -s <lib/resources/deploy/count-migrations.sh

  [ "$status" -ne 0 ]
  [ "$output" = "" ]
}
