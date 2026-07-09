#!/usr/bin/env bash
#
# Lowers the Docker daemon's minimum API version so Testcontainers (which speaks API 1.32)
# can talk to this bleeding-edge daemon (default floor 1.40). Run on the host with sudo:
#
#   bash scripts/fix-docker-min-api.sh
#
# After it finishes, check the printed "minAPI" line:
#   - minAPI 1.24  -> fixed; Testcontainers will connect.
#   - minAPI 1.40  -> this daemon ignores the override; use the downgrade path instead.
#
set -euo pipefail

DROPIN_DIR="/etc/systemd/system/docker.service.d"
DROPIN_FILE="${DROPIN_DIR}/min-api.conf"

echo "==> Writing systemd drop-in ${DROPIN_FILE}"
sudo mkdir -p "${DROPIN_DIR}"
sudo tee "${DROPIN_FILE}" >/dev/null <<'EOF'
[Service]
Environment=DOCKER_MIN_API_VERSION=1.24
EOF

echo "==> Reloading systemd and restarting Docker"
sudo systemctl daemon-reload
sudo systemctl restart docker

echo "==> Waiting for the daemon to come back"
for _ in $(seq 1 15); do
  if docker version >/dev/null 2>&1; then break; fi
  sleep 1
done

echo -n "==> Result: "
docker version --format 'server {{.Server.Version}} · minAPI {{.Server.MinAPIVersion}}'

MIN_API="$(docker version --format '{{.Server.MinAPIVersion}}' 2>/dev/null || echo '?')"
echo
if [ "${MIN_API}" = "1.24" ]; then
  echo "OK: minimum API lowered to 1.24 — Testcontainers should work now."
else
  echo "NOT APPLIED: minAPI is still ${MIN_API}. This daemon ignores DOCKER_MIN_API_VERSION;"
  echo "we'll fall back to downgrading docker-ce. Tell Claude the result."
fi
