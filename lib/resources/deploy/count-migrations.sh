#!/usr/bin/env bash
# Prints how many drizzle migrations the database has applied, or 0 when it has none yet.
# Run it in the deploy folder.
#
# Needs DATABASE_SERVICE.
set -euo pipefail

: "${DATABASE_SERVICE:?DATABASE_SERVICE is not set}"

# On the VPS this script arrives on standard input (bash -s). docker compose
# exec also reads standard input, so it gets /dev/null instead of the rest of the script.
run_sql() {
  docker compose exec -T "$DATABASE_SERVICE" sh -c 'psql --tuples-only --no-align -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"' sh "$1" </dev/null
}

table_exists="$(run_sql "SELECT to_regclass('drizzle.__drizzle_migrations') IS NOT NULL")"
if [ "$table_exists" != "t" ]; then
  echo 0
  exit 0
fi

run_sql "SELECT count(*) FROM drizzle.__drizzle_migrations"
