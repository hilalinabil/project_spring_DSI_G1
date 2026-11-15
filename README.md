# TicketFlow

**A hands-on reference project for learning Spring Cloud and microservices, built around a real-world ticket booking platform.**

TicketFlow is an open-source educational project that takes you from
"what is a microservice?" to building resilient, event-driven, observable
distributed systems with the Spring ecosystem.

Instead of yet another e-commerce demo, it tackles problems that naturally
require distributed-systems patterns: limited seat inventory, concurrent
bookings, unreliable payment providers, and asynchronous notifications.

## What you will learn

- **Service discovery & routing**: Eureka and Spring Cloud Gateway
- **Centralized configuration**: Spring Cloud Config with dynamic refresh
- **Inter-service communication**: declarative REST clients with OpenFeign
- **Resilience**: circuit breaker, retry, timeout, bulkhead and fallbacks (Resilience4J)
- **Event-driven architecture**: asynchronous messaging with Apache Kafka
- **Distributed transactions**: Saga pattern, Outbox pattern, idempotency
- **Containerization**: Docker and Docker Compose
- **AI integration** *(later)*: natural-language search and RAG assistant with Spring AI

## Architecture

| Module | Role | Key technology | Status |
|---|---|---|---|
| `customer-service` | Customer profiles (CRUD) | Spring Web, JPA | Done |
| `api-gateway` | Single entry point, routing (`/api/customers/**` → `lb://customer-service`) | Spring Cloud Gateway | Done |
| `discovery-server` | Service registry | Eureka | Done |
| `event-service` | Events and seats catalog | Spring Web, JPA | Planned |
| `config-server` | Centralized configuration, served from a Git repo | Spring Cloud Config | Done |
| `booking-service` | Booking and seat locking | OpenFeign, Resilience4J | Planned |
| `payment-service` | Simulated payment provider (random failures) | Resilience4J | Planned |
| `notification-service` | Confirmation emails/SMS | Kafka consumer | Planned |
| `ai-assistant-service` | Natural-language search, recommendations | Spring AI | Later |

## Roadmap

Each step has a Git tag and a lesson in `docs/lessons/`.

| Tag | Content |
|---|---|
| `step-00-setup` | Parent POM, repo structure, CI |
| `step-01-customer-service` | First business service |
| `step-02-gateway-static` | Gateway with static routes |
| `step-03-discovery` | Eureka + load-balanced routing |
| `step-04-event-service` | Second business service |
| `step-05-config` | Config Server |
| `step-06-booking-feign` | booking-service + OpenFeign |
| `step-07-resilience` | payment-service + Resilience4J |
| `step-08-kafka` | Events + notification-service |
| `step-09-saga` | Saga, Outbox, idempotency |
| `step-10-security` | Keycloak (customer-service integration) |
| `step-11-observability` | Tracing and metrics |
| `step-12-ai` | Spring AI |

To follow a lesson, check out its tag: `git checkout step-01-customer-service`.

## Tech stack

Java 21 · Spring Boot 4 · Spring Cloud 2025.1 · Resilience4J · Apache Kafka ·
MySQL 8.4 · Docker / Docker Compose · Spring AI (later)

## Repository structure

    ticketflow/
    ├── docker-compose.yml       # full stack (MySQL + all services)
    ├── docker/mysql/init/       # MySQL init script (databases + per-service users)
    ├── .env.example             # template for the git-ignored .env (secrets)
    ├── docs/                    # lessons, ADRs, templates
    └── services/                # one folder per microservice, each with its own README and Dockerfile

The configuration served by the config server lives in a **separate** Git
repository, [`ticketFlow-config`](https://github.com/hilalinabil/ticketFlow-config)
(branch `main`): `application.yaml` (shared by all config clients),
`customer-service.yaml`, `customer-service-dev.yaml` and `api-gateway.yaml`.

Every module is independently runnable and testable, and documents itself in
its own `README.md`.

## Getting started

Prerequisites: JDK 21, Maven (or the included wrapper), Docker.

    git clone https://github.com/hilalinabil/project_spring_DSI_G1.git
    cd project_spring_DSI_G1
    ./mvnw clean verify

The tests do not need a running Config Server or Eureka: each client module has
a test copy of its `application.yml`/`application.yaml` that disables both and
inlines the values normally read from Git.

## Architecture at runtime

    client ──► api-gateway :8080 ──lb://──► customer-service :8081 ──► MySQL customer_db
                   │                              │
                   └──── discovery-server :8761 ◄─┘   (Eureka registry)
       all Java services ──► config-server :8888 ──► GitHub ticketFlow-config

| Service | Port | Published on the host (Docker) | Database |
|---|---|---|---|
| `api-gateway` | 8080 | yes, `8080` | - |
| `discovery-server` | 8761 | yes, `8761` (dashboard) | - |
| `config-server` | 8888 | no | - |
| `customer-service` | 8081 | no (only through the gateway) | `customer_db` |
| `mysql` | 3306 | `127.0.0.1:3306` only (loopback, not the LAN) | `customer_db`, `event_db`, `booking_db` |

One MySQL server, **one database per business service**, each with its own
user that has privileges **only** on its own database (`customer_user` →
`customer_db`, `event_user` → `event_db`, `booking_user` → `booking_db`).
No service reads another service's database.

## Environment (`.env`)

All secrets live **only** in a root `.env` file, which is git-ignored.
`.env.example` (committed) lists the variables with fake values:

    cp .env.example .env      # then replace every change-me-* value

| Variable | Used by | Purpose |
|---|---|---|
| `CONFIG_GIT_URI` | config-server | GitHub URL of `ticketFlow-config` (never a `file:` URI) |
| `CONFIG_GIT_USERNAME` / `CONFIG_GIT_TOKEN` | config-server | only if the config repo becomes private |
| `MYSQL_ROOT_PASSWORD` | mysql | root password |
| `CUSTOMER_DB_PASSWORD` / `EVENT_DB_PASSWORD` / `BOOKING_DB_PASSWORD` | mysql init script + each service | per-service DB user passwords |

The MySQL init script (`docker/mysql/init/01-create-databases.sh`) runs only on
the **first** start of an empty data volume. If you change the passwords
afterwards, recreate the volume: `docker compose down -v` (this deletes the data).

Non-secret settings (ports, datasource URLs, JPA, routes) live in the config
repo. Hosts are placeholders with localhost defaults, so the same config works
inside and outside Docker:

| Placeholder (config repo) | Default (outside Docker) | Value in Docker |
|---|---|---|
| `${EUREKA_URL}` | `http://localhost:8761/eureka/` | `http://discovery-server:8761/eureka/` |
| `${DB_HOST}` | `localhost` | `mysql` |
| `${DB_USERNAME}` / `${DB_PASSWORD}` | no default: export them | from `.env` |
| `${CONFIG_SERVER_URL}` (api-gateway import) | `http://localhost:8888` | `http://config-server:8888` |

## Running with Docker Compose (recommended)

    cp .env.example .env            # once, then set real passwords
    docker compose up -d --build
    docker compose ps               # wait until every container is "healthy"

Startup order is enforced with healthchecks (`depends_on: condition: service_healthy`):
`mysql` + `config-server` → `discovery-server` → `customer-service` → `api-gateway`.

Every service image is built from the same two-stage Dockerfile
(`services/<service>/Dockerfile`, build context = repo root): a JDK 21 + Maven
wrapper build stage, and a JRE 21 runtime stage that runs as the non-root user
`ticketflow` (UID/GID 10001), with a `HEALTHCHECK` on `/actuator/health`.

Useful commands:

    docker compose logs -f customer-service
    docker compose restart customer-service    # data survives: it is in MySQL
    docker compose down                        # stop (keeps the MySQL volume)
    docker compose down -v                     # stop AND delete the MySQL data

### Why customer-service has 4 extra environment variables in Docker

`customer-service`'s own `src/main/resources/application.yml` is frozen (not
edited in this step) and hard-codes `spring.config.import:
"configserver:http://localhost:8888"`. Inside a container, `localhost` is the
container itself, so that import fails.

Setting only `SPRING_CONFIG_IMPORT` is **not** enough: Spring Boot processes
`spring.config.import` per file, so the env var *adds* a second import while
the file's `localhost:8888` import is still attempted, and because it is not
`optional:` the app refuses to start (`Could not locate PropertySource and the
resource is not optional`). So `docker-compose.yml` sets:

| Variable | Value | Why |
|---|---|---|
| `SPRING_CONFIG_LOCATION` | `optional:file:/app/config/` (empty) | do not load the packaged `application.yml` at all |
| `SPRING_APPLICATION_NAME` | `customer-service` | replaces `spring.application.name` from that file (selects `customer-service.yaml`) |
| `SPRING_CLOUD_CONFIG_LABEL` | `main` | replaces `spring.cloud.config.label` from that file |
| `SPRING_CONFIG_IMPORT` | `configserver:http://config-server:8888` | the import, with the Docker host name |

Everything else it needs (port, datasource, `ddl-auto: update`,
`open-in-view: false`, actuator exposure) comes from `customer-service.yaml`
in the config repo, which also overrides the local `create-drop` when run
outside Docker. The new services (and `api-gateway`) use
`configserver:${CONFIG_SERVER_URL:http://localhost:8888}` instead, so they only
need `CONFIG_SERVER_URL`.

## Running a service outside Docker

MySQL is published on `127.0.0.1:3306` (loopback only), so you can run one or
more services with Maven against the MySQL container. The config server is not
published, so run it locally too:

    docker compose up -d mysql discovery-server   # MySQL + Eureka (8761)
    ./mvnw -pl services/config-server spring-boot:run      # reads CONFIG_GIT_URI from .env

    # in another terminal: DB credentials come only from the environment
    export DB_USERNAME=customer_user
    export DB_PASSWORD=$(grep ^CUSTOMER_DB_PASSWORD= .env | cut -d= -f2)
    ./mvnw -pl services/customer-service spring-boot:run   # DB_HOST defaults to localhost

(Stop the Docker `customer-service` / `api-gateway` first if you run the same
service on the host, or Eureka will list both instances.)

The config server reads `CONFIG_GIT_URI` from `.env` by itself when run with
Maven. A real environment variable always takes precedence over `.env`.
For a private config repo, set `CONFIG_GIT_USERNAME` and `CONFIG_GIT_TOKEN` in
`.env` and uncomment the `username` / `password` lines in
`services/config-server/src/main/resources/application.yaml`.

Config clients do **not** use `optional:`, so they refuse to start if the
config server is unreachable.

## Endpoints

Through the gateway (`http://localhost:8080`):

| Method | Path | Description |
|---|---|---|
| POST | `/api/customers` | Create a customer → 201 + `Location` |
| GET | `/api/customers` | List customers |
| GET | `/api/customers/{id}` | Get one customer (404 if missing) |
| PUT | `/api/customers/{id}` | Update a customer |
| DELETE | `/api/customers/{id}` | Delete a customer → 204 |

Example:

    curl -i -X POST localhost:8080/api/customers \
      -H 'Content-Type: application/json' \
      -d '{"firstName":"Sara","lastName":"Idrissi","email":"sara@example.com"}'
    curl localhost:8080/api/customers/1

Other:

- Eureka dashboard: <http://localhost:8761> (`CUSTOMER-SERVICE`, `API-GATEWAY`;
  the config server is intentionally not registered)
- Gateway routes: `curl localhost:8080/actuator/gateway/routes`

## Who is this for?

Java developers who know Spring Boot basics and want to move into
microservices, and anyone preparing for backend or architecture interviews.

<!--
## Contributing

Contributions, issues and exercises are welcome. A `CONTRIBUTING.md` will be
added soon.
-->

## License

To be defined (MIT recommended for an educational project).

## Author

**AHAIDOUS Khadija**   | [GitHub](https://github.com/kahaidous>) · [LinkedIn](https://www.linkedin.com/in/khadijaahaidous)