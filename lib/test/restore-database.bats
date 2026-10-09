#!/usr/bin/env bats

bats_require_minimum_version 1.5.0

setup() {
  export DEPLOY_ENV="staging"
  export DATABASE_SERVICE="db"
  export BACKUP_DIR="$BATS_TEST_TMPDIR/backups"
  mkdir -p "$BACKUP_DIR"

  # A stand-in for docker that records its arguments and what it was sent.
  stub_dir="$BATS_TEST_TMPDIR/bin"
  mkdir -p "$stub_dir"
  cat >"$stub_dir/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$@" >"$BATS_TEST_TMPDIR/docker-args"
cat >"$BATS_TEST_TMPDIR/docker-input"
exit "${DOCKER_EXIT_CODE:-0}"
STUB
  chmod +x "$stub_dir/docker"
  PATH="$stub_dir:$PATH"
}

@test "refuses to restore over production" {
  export DEPLOY_ENV="production"
  echo "SELECT 1;" >"$BACKUP_DIR/backup-1.sql"

  run --separate-stderr bash lib/resources/deploy/restore-database.sh

  [ "$status" -ne 0 ]
  [ ! -e "$BATS_TEST_TMPDIR/docker-args" ]
}

@test "fails when the backup folder has no dumps" {
  run --separate-stderr bash lib/resources/deploy/restore-database.sh

  [ "$status" -ne 0 ]
  [ ! -e "$BATS_TEST_TMPDIR/docker-args" ]
}

@test "refuses an empty newest dump instead of using an older one" {
  echo "older dump" >"$BACKUP_DIR/backup-1.sql"
  touch -d "2026-01-01" "$BACKUP_DIR/backup-1.sql"
  : >"$BACKUP_DIR/backup-2.sql"

  run --separate-stderr bash lib/resources/deploy/restore-database.sh

  [ "$status" -ne 0 ]
  [ ! -e "$BATS_TEST_TMPDIR/docker-args" ]
}

@test "restores the newest dump" {
  echo "newer dump" >"$BACKUP_DIR/backup-a.sql"
  echo "older dump" >"$BACKUP_DIR/backup-b.sql"
  touch -d "2026-01-01" "$BACKUP_DIR/backup-b.sql"

  run bash lib/resources/deploy/restore-database.sh

  [ "$status" -eq 0 ]
  grep -q "newer dump" "$BATS_TEST_TMPDIR/docker-input"
  [ "$(grep -c "older dump" "$BATS_TEST_TMPDIR/docker-input")" -eq 0 ]
}

@test "stops on the first SQL error, in one transaction" {
  echo "SELECT 1;" >"$BACKUP_DIR/backup-1.sql"

  run bash lib/resources/deploy/restore-database.sh

  [ "$status" -eq 0 ]
  grep -q -- "--single-transaction -v ON_ERROR_STOP=1" "$BATS_TEST_TMPDIR/docker-args"
}

@test "fails when psql fails" {
  echo "SELECT 1;" >"$BACKUP_DIR/backup-1.sql"
  export DOCKER_EXIT_CODE=3

  run bash lib/resources/deploy/restore-database.sh

  [ "$status" -ne 0 ]
}
