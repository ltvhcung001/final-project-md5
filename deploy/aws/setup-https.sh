#!/usr/bin/env bash
# Points Nginx at the API gateway and gets a free HTTPS certificate (Let's Encrypt) for your domain.
# Run it ON THE SERVER after the DNS record for api.<domain> points to this server's IP:
#   bash ~/omnichannel/deploy/aws/setup-https.sh
# Non-interactive:
#   DOMAIN=example.com EMAIL=me@example.com bash setup-https.sh
# Add WITH_GRAFANA=yes to also publish grafana.<domain> (needs its own DNS record and the monitoring profile).
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="$DEPLOY_DIR/nginx/api.conf"

# default DOMAIN from deploy/.env when present
if [ -z "${DOMAIN:-}" ] && [ -f "$DEPLOY_DIR/.env" ]; then
  DOMAIN="$(grep -E '^DOMAIN=' "$DEPLOY_DIR/.env" | cut -d= -f2-)"
fi
if [ -z "${DOMAIN:-}" ]; then read -r -p "Your domain (for example example.com): " DOMAIN; fi
if [ -z "${EMAIL:-}" ]; then read -r -p "Email for Let's Encrypt expiry notices: " EMAIL; fi
[ -n "$DOMAIN" ] && [ -n "$EMAIL" ] || { echo "DOMAIN and EMAIL are required"; exit 1; }

API_HOST="api.$DOMAIN"
HOSTS=(-d "$API_HOST")
if [ "${WITH_GRAFANA:-no}" = "yes" ]; then HOSTS+=(-d "grafana.$DOMAIN"); fi

echo "==> checking DNS"
# public IPv4 of this server (AWS metadata service v2, with a public fallback)
TOKEN=$(curl -fsS -m 3 -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60" 2>/dev/null || true)
SERVER_IP=$(curl -fsS -m 3 -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || curl -fsS -m 5 https://checkip.amazonaws.com)
DNS_IP=$(getent ahostsv4 "$API_HOST" | awk '{print $1; exit}' || true)
echo "this server : $SERVER_IP"
echo "$API_HOST : ${DNS_IP:-<does not resolve>}"
if [ "$SERVER_IP" != "$DNS_IP" ]; then
  cat <<EOF

DNS is not ready: $API_HOST must have an A record pointing to $SERVER_IP.
Add it at your domain provider (see DEPLOY.md, step 2), wait a few minutes, and run this script again.
(Cloudflare users: set the record to "DNS only", the grey cloud, until the certificate is issued.)
EOF
  exit 1
fi

echo "==> nginx site"
sudo cp "$TEMPLATE" /etc/nginx/sites-available/omnichannel
sudo sed -i "s/DOMAIN/$DOMAIN/g" /etc/nginx/sites-available/omnichannel
sudo ln -sf /etc/nginx/sites-available/omnichannel /etc/nginx/sites-enabled/omnichannel
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx

echo "==> certificate"
sudo certbot --nginx "${HOSTS[@]}" --non-interactive --agree-tos -m "$EMAIL" --redirect

echo "==> renewal check (certbot renews automatically through a systemd timer)"
sudo certbot renew --dry-run >/dev/null && echo "renewal OK"

cat <<EOF

HTTPS is ready: https://$API_HOST
(a 502 Bad Gateway is normal until the application containers have been deployed)
EOF
