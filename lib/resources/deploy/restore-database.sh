#!/usr/bin/env bash
# Replaces the database of a non-production environment with the newest dump in a backup folder.
# Run it in the environment's deploy folder.
#
# Needs DEPLOY_ENV, DATABASE_SERVICE and BACKUP_DIR.
set -euo pipefail

: "${DEPLOY_ENV:?DEPLOY_ENV is not set}"
: "${DATABASE_SERVICE:?DATABASE_SERVICE is not set}"
: "${BACKUP_DIR:?BACKUP_DIR is not set}"

if [ "$DEPLOY_ENV" = "production" ]; then
  echo "Refusing to restore over production." >&2
  exit 1
fi

# shellcheck disable=SC2012
newest_dump="$(ls -t "$BACKUP_DIR"/*.sql 2>/dev/null | head -n 1 || true)"
if [ -z "$newest_dump" ]; then
  echo "No .sql dumps in $BACKUP_DIR." >&2
  exit 1
fi
if [ ! -s "$newest_dump" ]; then
  echo "The newest dump, $newest_dump, is empty." >&2
  exit 1
fi

echo "Restoring $newest_dump into $DEPLOY_ENV."

# Every schema except the system ones is dropped, then the dump recreates
# them. It all runs in one transaction, so a failed restore changes nothing.
drop_schemas="
DO \$\$
DECLARE schema_name text;
BEGIN
  FOR schema_name IN
    SELECT nspname FROM pg_namespace
    WHERE nspname NOT LIKE 'pg\\_%' AND nspname <> 'information_schema'
  LOOP
    EXECUTE format('DROP SCHEMA %I CASCADE', schema_name);
  END LOOP;
END
\$\$;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO public;
"

{
  printf '%s\n' "$drop_schemas"
  cat "$newest_dump"
} | docker compose exec -T "$DATABASE_SERVICE" sh -c 'psql --quiet --single-transaction -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
