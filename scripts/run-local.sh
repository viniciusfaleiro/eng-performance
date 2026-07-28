#!/usr/bin/env bash
#
# Build the app, package it as a Docker image, and bring up the full local environment
# (PostgreSQL + the app) with docker compose. One command to run everything locally.
#
#   ./scripts/run-local.sh          build image + start db & app  → http://localhost:8080
#   ./scripts/run-local.sh --down   stop and remove the containers (keeps the DB volume)
#   ./scripts/run-local.sh --logs   follow the app logs
#
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE="eng-performance:local"

case "${1:-up}" in
  --down|down)
    echo "==> Stopping the local environment…"
    docker compose --profile full down
    exit 0 ;;
  --logs|logs)
    docker compose --profile full logs -f app
    exit 0 ;;
esac

echo "==> [1/4] Building the boot jar (./gradlew :bootstrap:bootJar)…"
./gradlew :bootstrap:bootJar -q

JAR="$(ls app/bootstrap/build/libs/bootstrap-*-SNAPSHOT.jar | grep -v -- '-plain' | head -1)"
if [[ -z "${JAR}" ]]; then
  echo "!! boot jar not found under app/bootstrap/build/libs" >&2
  exit 1
fi

echo "==> [2/4] Building the Docker image ${IMAGE} (from ${JAR})…"
cp "${JAR}" app.jar
trap 'rm -f app.jar' EXIT
docker build -t "${IMAGE}" .

echo "==> [3/4] Starting db + app (docker compose, profile \"full\")…"
docker compose --profile full up -d

echo "==> [4/4] Waiting for the app on http://localhost:8080 …"
for _ in $(seq 1 90); do
  if curl -fsS -o /dev/null http://localhost:8080/; then
    echo ""
    echo "✔ Up and running → http://localhost:8080"
    echo "   logs:  ./scripts/run-local.sh --logs"
    echo "   stop:  ./scripts/run-local.sh --down"
    exit 0
  fi
  sleep 2
done

echo "!! The app did not become healthy in time. Check: docker compose --profile full logs app" >&2
exit 1
