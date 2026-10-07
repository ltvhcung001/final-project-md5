#!/usr/bin/env bash
# Seeds demo data through the public API: a category, a brand, products and their stock.
# Requires: curl, jq. The admin account comes from ADMIN_EMAIL / ADMIN_PASSWORD of identity-service.
#   BASE=http://localhost:8080 ./scripts/seed.sh
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@12345}"
FLASH_STOCK="${FLASH_STOCK:-10}"

api() { # method path [json]
  local method=$1 path=$2 body=${3:-}
  if [ -n "$body" ]; then
    curl -fsS -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$body"
  else
    curl -fsS -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN"
  fi
}

echo "logging in as $ADMIN_EMAIL"
TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r '.data.accessToken')

CATEGORY=$(api POST /api/categories '{"name":"Smartphones"}' | jq -r '.data.id')
BRAND=$(api POST /api/brands '{"name":"Omni"}' | jq -r '.data.id')

create_product() { # name sku price stock
  api POST /api/products "$(jq -n --arg name "$1" --arg sku "$2" --argjson price "$3" --arg c "$CATEGORY" --arg b "$BRAND" \
    '{name:$name, description:("Demo product " + $name), categoryId:$c, brandId:$b, images:[],
      variants:[{sku:$sku, name:"Standard", price:$price, attributes:{color:"black"}}]}')" >/dev/null
  api PUT "/api/inventory/$2" "{\"available\":$4}" >/dev/null
  echo "  product $1 sku=$2 price=$3 stock=$4"
}

echo "creating products"
create_product "Omni Phone Flash Sale Edition" FLASH-001 199000 "$FLASH_STOCK"
create_product "Omni Phone Standard" PHONE-001 5990000 500
create_product "Omni Earbuds" EARBUD-001 790000 500

echo "seed done. Try: curl '$BASE/api/products?q=omni'"
