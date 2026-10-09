#!/usr/bin/env bash
# Rebuilds the Jenkins image and restarts Jenkins with the image's plugins.
# Run it after changing image/Dockerfile or the plugin files.
set -euo pipefail

jenkins_home="/var/jenkins_home"

cd "$(dirname "$0")/.."

read -r -p "Restarting Jenkins stops any running build. Continue? (y/N) " answer
if [ "$answer" != "y" ]; then
  echo "Nothing changed."
  exit 1
fi

# The build runs while Jenkins is still up, so a failed build leaves it running.
docker compose build

docker compose down

rm -rf "$jenkins_home/plugins"

docker compose up --detach

echo "Waiting for Jenkins to start."
for _ in $(seq 1 60); do
  if docker compose logs jenkins 2>&1 | grep -q "Jenkins is fully up and running"; then
    echo "Jenkins is up."
    exit 0
  fi
  sleep 2
done

echo "Jenkins did not start within 2 minutes. The last log lines:" >&2
docker compose logs --tail 30 jenkins >&2
exit 1
