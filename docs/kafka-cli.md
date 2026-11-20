# Kafka CLI cheat sheet (TicketFlow)

TP6 commands adapted to the TicketFlow stack: a single Kafka 4.0 node in KRaft
mode (`kafka` service in `docker-compose.yml`). Run from the repository root.

| Listener | Address | Used by |
|---|---|---|
| PLAINTEXT | `kafka:9092` | containers on the `ticketflow` network (services, CLI below) |
| EXTERNAL | `localhost:9094` | the host only (published on `127.0.0.1`) |
| CONTROLLER | `:9093` | KRaft quorum, internal, not published |

Every command below runs **inside the broker container** and talks to the
internal listener:

```bash
K="docker compose exec kafka /opt/kafka/bin"   # add -T when piping input
BS=kafka:9092
```

Topics are created only by the one-shot `kafka-init` container
(`docker/kafka/create-topics.sh`); `auto.create.topics.enable=false`, so
producing to a misspelled topic fails instead of creating it.

| Topic | Key | Partitions / RF |
|---|---|---|
| `ticketflow.booking.created` | bookingId | 3 / 1 |
| `ticketflow.booking.cancelled` | bookingId | 3 / 1 |
| `ticketflow.payment.succeeded` | bookingId | 3 / 1 |
| `ticketflow.payment.failed` | bookingId | 3 / 1 |

Same key → same partition → events of one booking are read in order.

---

## Topics

```bash
# List
$K/kafka-topics.sh --bootstrap-server $BS --list

# Describe one topic, or all TicketFlow topics (regex)
$K/kafka-topics.sh --bootstrap-server $BS --describe --topic ticketflow.booking.created
$K/kafka-topics.sh --bootstrap-server $BS --describe --topic 'ticketflow\..*'

# Create a throwaway topic for experiments (idempotent), delete it afterwards
$K/kafka-topics.sh --bootstrap-server $BS --create --if-not-exists \
  --topic ticketflow.test.keys --partitions 3 --replication-factor 1
$K/kafka-topics.sh --bootstrap-server $BS --delete --topic ticketflow.test.keys

# Re-run the topic creation (safe: --if-not-exists)
docker compose run --rm kafka-init
```

## Produce (with keys)

`key:value`, one message per line. Interactive (Ctrl+D to finish):

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server kafka:9092 --topic ticketflow.test.keys \
  --property parse.key=true --property key.separator=:
> booking-40:PaymentSucceeded
> booking-40:BookingConfirmed
```

Non-interactive (note `-T`):

```bash
printf '%s\n' booking-40:PaymentSucceeded booking-41:PaymentSucceeded booking-40:TicketIssued \
  | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server kafka:9092 --topic ticketflow.test.keys \
      --property parse.key=true --property key.separator=:
```

## Consume

```bash
# Everything from the beginning, with partition / offset / key
$K/kafka-console-consumer.sh --bootstrap-server $BS --topic ticketflow.test.keys \
  --from-beginning --property print.partition=true --property print.offset=true \
  --property print.key=true

# One partition from a given offset, then stop
$K/kafka-console-consumer.sh --bootstrap-server $BS --topic ticketflow.test.keys \
  --partition 1 --offset 0 --max-messages 5 --property print.key=true

# As a member of a consumer group (offsets are committed for the group).
# Start it in two terminals to see the partitions split 2 + 1.
$K/kafka-console-consumer.sh --bootstrap-server $BS --topic ticketflow.test.keys \
  --group notification-service --property print.partition=true --property print.key=true
```

A consumer without `--group` creates a random `console-consumer-NNNNN` group:
delete it afterwards (see below).

## Consumer groups (lag)

```bash
$K/kafka-consumer-groups.sh --bootstrap-server $BS --list

# Per partition: CURRENT-OFFSET, LOG-END-OFFSET, LAG, CONSUMER-ID
$K/kafka-consumer-groups.sh --bootstrap-server $BS --describe --group notification-service

# Members and their assigned partitions / group state
$K/kafka-consumer-groups.sh --bootstrap-server $BS --describe --group notification-service --members --verbose
$K/kafka-consumer-groups.sh --bootstrap-server $BS --describe --group notification-service --state
```

## Reset offsets (replay)

The group must have **no active member**. Always `--dry-run` first.

```bash
# Preview
$K/kafka-consumer-groups.sh --bootstrap-server $BS --group notification-service \
  --topic ticketflow.test.keys --reset-offsets --to-earliest --dry-run

# Apply (other options: --to-latest, --shift-by -2, --to-offset 3,
#        --to-datetime 2026-11-20T00:00:00.000)
$K/kafka-consumer-groups.sh --bootstrap-server $BS --group notification-service \
  --topic ticketflow.test.keys --reset-offsets --to-earliest --execute

# Delete a group (no active member)
$K/kafka-consumer-groups.sh --bootstrap-server $BS --delete --group notification-service
```

## Offsets of a topic

```bash
# Log-end offset per partition (topic:partition:offset); --time -2 = earliest
$K/kafka-get-offsets.sh --bootstrap-server $BS --topic ticketflow.booking.created
$K/kafka-get-offsets.sh --bootstrap-server $BS --topic 'ticketflow\..*' --time -2
```

## Broker

```bash
# Is the broker up? (this is the container's healthcheck)
$K/kafka-broker-api-versions.sh --bootstrap-server $BS | head -1

# Who runs it (non-root: uid=1000(appuser))
docker compose exec kafka id
```

## From the host (localhost:9094)

No Kafka install needed: run the image with the host's network.

```bash
docker run --rm -it --network host apache/kafka:4.0.0 \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9094 --list
```

A Spring Boot service started with `./mvnw spring-boot:run` uses the same
address (`spring.kafka.bootstrap-servers` defaults to `localhost:9094` in the
config repo; containers get `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`).

## Data

Topics and messages live in the named volume `ticketflow_kafka-data`: they
survive `docker compose restart kafka` and `docker compose down` / `up`.
`docker compose down -v` deletes them (kafka-init recreates the empty topics).
