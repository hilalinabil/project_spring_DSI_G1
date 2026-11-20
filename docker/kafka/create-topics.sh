#!/usr/bin/env bash
# Creates the TicketFlow topics. Run by the one-shot kafka-init container
# (docker compose), safe to run again: --if-not-exists.
# Naming: ticketflow.<aggregate>.<past-tense event>. 3 partitions, RF 1 (one broker).
# Producers use the bookingId as key: same booking -> same partition -> ordered.
set -euo pipefail

BS="${BOOTSTRAP_SERVER:-kafka:9092}"
TOPICS=(
  ticketflow.booking.created
  ticketflow.booking.cancelled
  ticketflow.payment.succeeded
  ticketflow.payment.failed
)

for topic in "${TOPICS[@]}"; do
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor 1
done

echo "Topics on $BS:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" --list
