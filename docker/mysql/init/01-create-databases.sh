#!/bin/bash
# Runs once, on the first start of the mysql container (empty data volume).
# One database per business service, each with its own user that has
# privileges ONLY on its own database. Passwords come from the environment
# (root .env, git-ignored), so nothing secret is committed.
set -euo pipefail

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${CUSTOMER_DB_PASSWORD:?CUSTOMER_DB_PASSWORD is required}"
: "${EVENT_DB_PASSWORD:?EVENT_DB_PASSWORD is required}"
: "${BOOKING_DB_PASSWORD:?BOOKING_DB_PASSWORD is required}"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-SQL
	CREATE DATABASE IF NOT EXISTS customer_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
	CREATE DATABASE IF NOT EXISTS event_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
	CREATE DATABASE IF NOT EXISTS booking_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

	CREATE USER IF NOT EXISTS 'customer_user'@'%' IDENTIFIED BY '${CUSTOMER_DB_PASSWORD}';
	CREATE USER IF NOT EXISTS 'event_user'@'%'    IDENTIFIED BY '${EVENT_DB_PASSWORD}';
	CREATE USER IF NOT EXISTS 'booking_user'@'%'  IDENTIFIED BY '${BOOKING_DB_PASSWORD}';

	GRANT ALL PRIVILEGES ON customer_db.* TO 'customer_user'@'%';
	GRANT ALL PRIVILEGES ON event_db.*    TO 'event_user'@'%';
	GRANT ALL PRIVILEGES ON booking_db.*  TO 'booking_user'@'%';
	FLUSH PRIVILEGES;
SQL
echo "TicketFlow: customer_db, event_db, booking_db and their users created."
