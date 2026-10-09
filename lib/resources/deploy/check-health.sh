#!/usr/bin/env bash
# Pings a URL for a healthy answer, and tries again a few times while the app starts. Exits 0 on the first success, or 1 when every try fails.
#
# Needs HEALTH_URL. HEALTH_TRIES and HEALTH_WAIT_SECONDS are optional.
set -euo pipefail

: "${HEALTH_URL:?HEALTH_URL is not set}"
tries="${HEALTH_TRIES:-10}"
wait_seconds="${HEALTH_WAIT_SECONDS:-6}"

for attempt in $(seq 1 "$tries"); do
  if curl --fail --silent --show-error --max-time 10 --output /dev/null "$HEALTH_URL"; then
    echo "Healthy: $HEALTH_URL"
    exit 0
  fi
  echo "Try $attempt of $tries failed."
  if [ "$attempt" -lt "$tries" ]; then
    sleep "$wait_seconds"
  fi
done

echo "No healthy answer from $HEALTH_URL after $tries tries." >&2
exit 1
