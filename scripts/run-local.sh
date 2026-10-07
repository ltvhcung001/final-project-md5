#!/usr/bin/env bash
# Starts the six services from their built jars (run `mvn -DskipTests package` first and
# `docker compose -f deploy/docker-compose.local.yml up -d` for the infrastructure).
# Logs go to ./logs/<service>.log, PIDs to ./logs/pids. Stop with scripts/stop-local.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p logs
: > logs/pids

export JWT_SECRET="${JWT_SECRET:-local-dev-secret-local-dev-secret-1234}"
export ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@12345}"
export RATE_LIMIT_RPM="${RATE_LIMIT_RPM:-1000000}"   # the default of 120/min would throttle load tests
export PAYMENT_MOCK_ENABLED="${PAYMENT_MOCK_ENABLED:-true}"
JAVA_OPTS="${JAVA_OPTS:--Xmx384m}"

for svc in identity-service product-service order-inventory-service payment-service notification-service api-gateway; do
  echo "starting $svc"
  nohup java $JAVA_OPTS -jar "$svc/target/$svc.jar" > "logs/$svc.log" 2>&1 &
  echo $! >> logs/pids
done

echo "waiting for the gateway on :8080 ..."
for i in $(seq 1 60); do
  if curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1; then echo "gateway is up"; break; fi
  sleep 3
done
