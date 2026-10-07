#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 22.04/24.04 EC2 instance, as the default "ubuntu" user.
# See DEPLOY.md step 3: copy the deploy/ folder to ~/omnichannel/ and run
#   bash ~/omnichannel/deploy/aws/bootstrap-ec2.sh
#
# What it does: swap file, Docker + compose plugin, Nginx, Certbot, a project folder. The firewall on AWS is the
# Security Group (allow only 22, 80, 443), so UFW is optional here; it is enabled anyway as defence in depth.
set -euo pipefail

SWAP_GB="${SWAP_GB:-2}"
APP_DIR="${APP_DIR:-$HOME/omnichannel}"

echo "==> swap (${SWAP_GB} GB)"
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l "${SWAP_GB}G" /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
  echo 'vm.swappiness=20' | sudo tee /etc/sysctl.d/99-swappiness.conf >/dev/null
  sudo sysctl -p /etc/sysctl.d/99-swappiness.conf >/dev/null
fi

echo "==> packages"
sudo apt-get update -y
sudo apt-get install -y ca-certificates curl gnupg nginx certbot python3-certbot-nginx ufw jq

echo "==> docker"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

echo "==> firewall (UFW; Docker-published ports bypass it, so compose only publishes on 127.0.0.1)"
sudo ufw default deny incoming
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable

echo "==> project folder $APP_DIR/deploy"
mkdir -p "$APP_DIR/deploy"

cat <<EOF

Done. Log out and back in once so the docker group applies, then:
  1. bash $APP_DIR/deploy/aws/init-env.sh      (creates deploy/.env with random secrets)
  2. bash $APP_DIR/deploy/aws/setup-https.sh   (Nginx + free HTTPS certificate; DNS must already point here)
  3. push to main: GitHub Actions builds the images and deploys (see DEPLOY.md)
EOF
