#!/usr/bin/env bash
# Creates deploy/.env from deploy/.env.example with strong random secrets filled in.
# Run it ON THE SERVER, from anywhere:
#   bash ~/omnichannel/deploy/aws/init-env.sh
# Non-interactive:
#   DOMAIN=example.com GHCR_OWNER=my-github-user ADMIN_EMAIL=me@example.com bash init-env.sh
# Existing .env is never overwritten (delete it first if you want to start over).
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
EXAMPLE="$DEPLOY_DIR/.env.example"

if [ -f "$ENV_FILE" ]; then
  echo "$ENV_FILE already exists, not touching it."
  exit 0
fi
[ -f "$EXAMPLE" ] || { echo "missing $EXAMPLE"; exit 1; }

ask() { # var prompt
  local var=$1 prompt=$2
  if [ -z "${!var:-}" ]; then read -r -p "$prompt: " "$var"; fi
  [ -n "${!var}" ] || { echo "$var is required"; exit 1; }
}
rand() { openssl rand -base64 "${1:-24}" | tr -d '/+=\n' | cut -c1-"${2:-32}"; }
set_var() { # KEY VALUE  (values are generated or validated, so no characters that need escaping)
  sed -i "s|^$1=.*|$1=$2|" "$ENV_FILE"
}

ask DOMAIN      "Your domain (for example example.com, the API will be api.example.com)"
ask GHCR_OWNER  "Your GitHub user or organisation name (where the Docker images are published)"
ask ADMIN_EMAIL "Email for the first admin account"

cp "$EXAMPLE" "$ENV_FILE"
chmod 600 "$ENV_FILE"

ADMIN_PASSWORD="$(rand 24 20)A1!"   # letters/digits plus a suffix that satisfies most password rules

set_var DOMAIN "$DOMAIN"
set_var GHCR_OWNER "$(echo "$GHCR_OWNER" | tr '[:upper:]' '[:lower:]')"   # GHCR image names must be lowercase
set_var ADMIN_EMAIL "$ADMIN_EMAIL"
set_var ADMIN_PASSWORD "$ADMIN_PASSWORD"
set_var JWT_SECRET "$(rand 48 64)"
set_var POSTGRES_ADMIN_PASSWORD "$(rand 24 28)"
set_var IDENTITY_DB_PASSWORD "$(rand 24 28)"
set_var ORDER_DB_PASSWORD "$(rand 24 28)"
set_var PAYMENT_DB_PASSWORD "$(rand 24 28)"
set_var RABBITMQ_PASSWORD "$(rand 24 28)"
set_var GRAFANA_ADMIN_PASSWORD "$(rand 24 20)"

# Pick a memory profile from the RAM of this machine
MEM_MB=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo 2>/dev/null || echo 8000)
if [ "$MEM_MB" -lt 6000 ]; then
  set_var JAVA_XMX 192m
  echo "Detected ${MEM_MB} MB RAM: set JAVA_XMX=192m (small instance profile)."
fi

cat <<EOF

Created $ENV_FILE (readable only by you).

  Admin login : $ADMIN_EMAIL
  Admin pass  : $ADMIN_PASSWORD     <- save it now, it is not shown again (it is also stored in the .env file)

Still empty on purpose (fill in with 'nano $ENV_FILE' when you need them):
  VNPAY_* / MOMO_*   payment sandbox credentials (the MOCK payment method works without them)
  MAIL_USER / MAIL_PASSWORD   SMTP login, for example from a free Mailtrap inbox

EOF
