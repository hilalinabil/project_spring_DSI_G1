# TicketFlow: setup guide for collaborators

This guide takes you from a fresh clone to the full TicketFlow stack running
on your machine (7 containers), and explains the team rules.

---

## 1. Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Git | any recent | cloning, branches |
| Docker + Docker Compose v2 | Docker 24+ (`docker compose`, not `docker-compose`) | running the stack |
| JDK | 21 | running the tests, or a service outside Docker (optional) |

You do **not** need Maven: the project ships the Maven wrapper (`./mvnw`).

These host ports must be **free**:

| Port | Used by |
|---|---|
| 8080 | api-gateway (the only public entry point) |
| 8761 | Eureka dashboard (published on `127.0.0.1` only) |
| 3306 | MySQL (published on `127.0.0.1` only) |

If you already run a local MySQL on 3306, stop it first.

**Windows users:** use WSL 2 (recommended) or Git Bash, and before cloning run:

```bash
git config --global core.autocrlf input
```

Otherwise the shell scripts (`mvnw`, `docker/mysql/init/*.sh`) get Windows line
endings and fail inside the containers.

---

## 2. Access

Ask the repository owner to add you as a collaborator on **both** repositories:

| Repository | Content |
|---|---|
| `hilalinabil/project_spring_DSI_G1` | the application (all microservices, Docker files) |
| `hilalinabil/ticketFlow-config` | the centralized configuration, read by the config server from GitHub |

You only need to clone the config repository if you plan to change the configuration
(see [section 7](#7-changing-the-configuration)).

---

## 3. First run

```bash
# 1. Clone the application
git clone git@github.com:hilalinabil/project_spring_DSI_G1.git ticketflow
cd ticketflow

# 2. Switch to the working branch (until it is merged into main)
git checkout feature-config

# 3. Create YOUR OWN .env file (git-ignored, never commit it)
cp .env.example .env
```

Edit `.env` and replace every `change-me-*` value with any password you like.
They are only used on your machine: you do **not** need the owner's passwords.

```dotenv
CONFIG_GIT_URI=https://github.com/hilalinabil/ticketFlow-config   # keep as is
MYSQL_ROOT_PASSWORD=<choose one>
CUSTOMER_DB_PASSWORD=<choose one>
EVENT_DB_PASSWORD=<choose one>
BOOKING_DB_PASSWORD=<choose one>
```

```bash
# 4. Build the images and start the stack
docker compose up -d --build
```

> The **first build is slow** (about 20 to 30 minutes): every image downloads
> all its Maven dependencies. Later builds reuse the Docker cache and take
> seconds to a few minutes.

```bash
# 5. Wait until all 7 containers are "healthy" (about 1 to 2 minutes after start)
docker compose ps
```

Expected:

```
SERVICE            STATUS
api-gateway        Up (healthy)
booking-service    Up (healthy)
config-server      Up (healthy)
customer-service   Up (healthy)
discovery-server   Up (healthy)
event-service      Up (healthy)
mysql              Up (healthy)
```

Startup order is automatic: `mysql` + `config-server` → `discovery-server` →
`customer-service` / `event-service` → `booking-service` / `api-gateway`.

---

## 4. Check that everything works

Open the Eureka dashboard: <http://localhost:8761>. You should see
`CUSTOMER-SERVICE`, `EVENT-SERVICE`, `BOOKING-SERVICE` and `API-GATEWAY` with
status **UP**. The gateway can take about 30 seconds to discover a service that has just started.

Then run this end-to-end test (everything goes through the gateway on port 8080):

```bash
# Create a customer -> 201
curl -i -X POST localhost:8080/api/customers -H 'Content-Type: application/json' \
  -d '{"firstName":"Sara","lastName":"Idrissi","email":"sara@example.com","phone":"0600000000"}'

# Create an event (DRAFT) -> 201, then publish it -> 200
curl -i -X POST localhost:8080/api/events -H 'Content-Type: application/json' \
  -d '{"title":"Gnaoua Night","description":"Live music","venue":"Theatre Mohammed V",
       "city":"Rabat","startDateTime":"2030-06-20T20:00:00","price":250.00,"capacity":100}'
curl -X PATCH localhost:8080/api/events/1/publish

# Book 2 seats -> 201, status PENDING_PAYMENT, totalPrice 500.00
curl -i -X POST localhost:8080/api/bookings -H 'Content-Type: application/json' \
  -d '{"customerId":1,"eventId":1,"quantity":2}'

# availableSeats is now 98
curl localhost:8080/api/events/1
```

(Use the ids returned by your own calls if they are not 1.)

---

## 5. Daily commands

```bash
./mvnw clean verify                              # all tests; no Docker needed (H2 + inline config)
./mvnw -pl services/booking-service -am verify   # tests of one module only

docker compose ps                                # status of the containers
docker compose logs -f booking-service           # follow one service's logs
docker compose up -d --build booking-service     # rebuild + restart one service after a code change
docker compose restart customer-service          # restart without rebuilding

docker compose down                              # stop everything, KEEPS the MySQL data
docker compose down -v                           # stop everything AND DELETES the MySQL data
```

---

## 6. Running a service outside Docker (optional, for debugging in the IDE)

MySQL and Eureka stay in Docker; the config server and the service you debug
run on your machine:

```bash
docker compose up -d mysql discovery-server
./mvnw -pl services/config-server spring-boot:run     # terminal 1 (reads CONFIG_GIT_URI from .env)

# terminal 2: database credentials only come from the environment
export DB_USERNAME=event_user
export DB_PASSWORD=$(grep ^EVENT_DB_PASSWORD= .env | cut -d= -f2)
./mvnw -pl services/event-service spring-boot:run
```

Use `customer_user` / `CUSTOMER_DB_PASSWORD` for customer-service and
`booking_user` / `BOOKING_DB_PASSWORD` for booking-service. Stop the Docker
container of the same service first (`docker compose stop event-service`),
otherwise Eureka lists two instances.

---

## 7. Changing the configuration

All non-secret settings (ports, datasource URLs, JPA, Feign timeouts, gateway
routes) live in the **`ticketFlow-config`** repository, branch `main`:

| File | Used by |
|---|---|
| `application.yaml` | every service (Eureka URL) |
| `api-gateway.yaml` | gateway port and routes |
| `customer-service.yaml`, `event-service.yaml`, `booking-service.yaml` | each service |

⚠️ **That branch is shared live.** Every collaborator's config server reads it
from GitHub, so a push to `main` changes **everyone's** running stack.
Always open a pull request for config changes; never push to `main` directly.

After a config change has been merged, restart the services that use it:

```bash
docker compose restart config-server event-service   # for example
```

---

## 8. Team rules

1. **Never commit `.env`** or any password or token. Secrets live only in your local `.env`.
2. **Never push to `main`.** Work on a branch, open a pull request; CI runs
   `./mvnw verify` on every PR and it must be green before merging.
3. **`./mvnw clean verify` must pass** locally before you open a PR.
4. **customer-service is frozen:** do not change its Java code, packages, DTOs or tests.
5. **One database per service:** a service never reads another service's database.
   Services talk to each other over HTTP (OpenFeign), through Eureka, never through the gateway.
6. **No shared DTO module:** each service owns its own records (for example, booking-service
   has its own `client/dto/EventResponse`).
7. **`CONFIG_GIT_URI` is always the GitHub URL**, never a `file:` URI to a local clone.
8. New services follow the existing layout (`controller/`, `dto/`, `entity/`, `repository/`,
   `mapper/`, `service/` + `service/impl/`, `exception/`) and the shared two-stage `Dockerfile`
   template (copy one, change only the module name and port). When you add a module, also add its
   `pom.xml` `COPY` line to **every** Dockerfile.

---

## 9. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `set MYSQL_ROOT_PASSWORD in .env` | `.env` is missing: do step 3 of section 3 |
| A service is `unhealthy` / restarting, logs show `Access denied for user` | You changed passwords in `.env` after the first start. The MySQL init script only runs on an **empty** volume: `docker compose down -v` then `docker compose up -d` (deletes the data) |
| `mysql` fails at first start with `$'\r': command not found` | Windows line endings in the init script: set `core.autocrlf input`, delete and re-clone |
| `port is already allocated` (3306, 8080 or 8761) | Another program uses the port; stop it (for example a local MySQL) |
| `config-server` never becomes healthy | No internet access to GitHub, or the config repo became private: set `CONFIG_GIT_USERNAME` and `CONFIG_GIT_TOKEN` (your own token) in `.env` |
| A service starts on the wrong port, or a route returns 404 | The config server could not serve its file: `docker compose logs config-server`, check the file exists on `main` in `ticketFlow-config` |
| `POST /api/bookings` returns 503 | event-service or customer-service is down: `docker compose ps`, then `docker compose start <service>` |
| A new route returns 404 just after startup | The gateway hasn't discovered the service yet; wait about 30 s |
| Everything is broken and you want a clean start | `docker compose down -v && docker compose up -d --build` |

---

## 10. Service map

```
client ──► api-gateway :8080 ──► customer-service :8081 ──► MySQL customer_db
                         ├─────► event-service    :8082 ──► MySQL event_db
                         └─────► booking-service  :8083 ──► MySQL booking_db
                                      │ OpenFeign (via Eureka)
                                      ├─► customer-service  GET /api/customers/{id}
                                      └─► event-service     /internal/events/**  (not routed by the gateway)

discovery-server :8761 (Eureka)      config-server :8888 ──► GitHub ticketFlow-config (main)
```

| Endpoint group | Paths (via `localhost:8080`) |
|---|---|
| Customers | `POST/GET /api/customers`, `GET/PUT/DELETE /api/customers/{id}` |
| Events | `POST/GET /api/events`, `GET /api/events/{id}`, `PATCH /api/events/{id}/publish`, `PATCH /api/events/{id}/close` |
| Bookings | `POST /api/bookings`, `GET /api/bookings/{id}`, `GET /api/bookings?customerId=` |

Errors are always JSON: `{"status","error","message","path","timestamp"}`
(400 invalid input, 404 not found, 409 business conflict, 503 dependency unavailable).
