#!/bin/bash
# One Postgres instance, three databases, one dedicated user per service, no cross access.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE USER identity WITH PASSWORD '${IDENTITY_DB_PASSWORD}';
CREATE USER orders   WITH PASSWORD '${ORDER_DB_PASSWORD}';
CREATE USER payment  WITH PASSWORD '${PAYMENT_DB_PASSWORD}';

CREATE DATABASE identity_db OWNER identity;
CREATE DATABASE order_db    OWNER orders;
CREATE DATABASE payment_db  OWNER payment;

REVOKE ALL ON DATABASE identity_db FROM PUBLIC;
REVOKE ALL ON DATABASE order_db    FROM PUBLIC;
REVOKE ALL ON DATABASE payment_db  FROM PUBLIC;
SQL
