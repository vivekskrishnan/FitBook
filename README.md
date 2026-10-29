# FitBook

CMPE 172 Term Project — Online Appointment Scheduling System (fitness-training scenario).

## Milestone 1 — Requirements, Design & Skeleton

### What this milestone implements

- A layered Spring Boot skeleton: `Controller -> Service -> Repository`, using hand-written SQL over `JdbcTemplate` (no ORM/JPA).
- A five-table relational schema (`schema.sql`): `users`, `services`, `trainers`, `availability_slots`, `appointments`.
- A double-booking guard: `appointments.slot_id` is `UNIQUE`, so the database rejects a second active appointment for the same slot at the constraint level, independent of application logic.
- Seed data (`seed.sql`) loaded automatically on startup so the endpoints return real data.
- Two read endpoints that query the database and return DTOs:
  - `GET /` — app summary (`HomeDTO`): users, trainers, services.
  - `GET /slots` — available appointment slots (`SlotsDTO`), joined across `availability_slots`, `trainers`, `users`, and `services`.

Not yet implemented (planned for later milestones): login/auth, booking/cancellation, provider slot management, notifications, logging/metrics, AI features. See the Milestone 1 report for the full feature roadmap.

### Tech stack

- Java 21
- Spring Boot 4.1.1 (Spring Web, Spring JDBC)
- MySQL 8+ (via `mysql-connector-j`)
- Maven (via the included wrapper, `mvnw` / `mvnw.cmd`)

### Prerequisites

- JDK 21 installed, with `JAVA_HOME` pointing at it.
- A running MySQL server, reachable at `localhost:3306`.

### Configure

The app reads DB credentials from required environment variables (see `fitbook/src/main/resources/application.properties`) — no credentials are hardcoded in the repo:

| Variable      | Purpose         |
|---------------|------------------|
| `DB_USERNAME` | MySQL username   |
| `DB_PASSWORD` | MySQL password   |

Create the database and a matching user in MySQL before first run (pick your own username/password):

```sql
CREATE DATABASE fitbook;
CREATE USER 'fitbook_user'@'localhost' IDENTIFIED BY 'your_password_here';
GRANT ALL PRIVILEGES ON fitbook.* TO 'fitbook_user'@'localhost';
FLUSH PRIVILEGES;
```

Then set the environment variables before running the app, e.g. in PowerShell:

```powershell
$env:DB_USERNAME = "fitbook_user"
$env:DB_PASSWORD = "your_password_here"
```

`schema.sql` and `seed.sql` run automatically on every startup (`spring.sql.init.mode=always`) — the app drops and recreates all five tables and reseeds sample data each time it boots, so no manual migration step is needed.

### Build and run

From the `fitbook/` directory:

```powershell
cd fitbook
.\mvnw.cmd spring-boot:run
```

(On macOS/Linux: `./mvnw spring-boot:run`.)

The app starts on **http://localhost:8080**.

### Verify it's working

```powershell
curl http://localhost:8080/
curl http://localhost:8080/slots
```

`GET /` returns users, trainers, and services from the database. `GET /slots` returns only `OPEN` availability slots, joined with trainer and service info.

## Milestone 3 — Integration, Distribution & Deployment

### What this milestone implements

- **Distribution boundary:** a mock notification service (`POST /mock-notifications`, a second controller in the same app) reached only over HTTP through a `NotificationGateway` client. One coarse-grained `NotificationRequestDTO` (idempotency key, recipient, type, message) is sent per business event.
- **Resilience:** the gateway uses connect/read timeouts, bounded retries with backoff (not on 4xx), and a fallback that logs and returns `false`, so a slow or down notification service never fails a booking.
- **Commit-then-notify:** `AppointmentFacade` is not transactional. `BookingService.book()/cancel()` commit first, then the notification is sent. A confirmation is sent on both book and cancel, and only on success.
- **Idempotency:** retries reuse the same key (`BOOKED-<id>` / `CANCELLED-<id>`); the mock service answers `DUPLICATE` for a repeated key.
- **Observability:** SLF4J logging (INFO/WARN/ERROR), `GET /health` (checks MySQL with `SELECT 1`; 200 UP / 503 DOWN), and Micrometer metrics at `/actuator/metrics/<name>`:
  `fitbook.bookings.success`, `fitbook.bookings.failed` (tag `reason=conflict`), `fitbook.booking.latency`, `fitbook.notifications.sent`, `fitbook.notifications.failed`.
- **Delivery:** multi-stage `Dockerfile`, `docker-compose.yml` (app + MySQL), and a GitHub Actions workflow (`.github/workflows/ci.yml`) that builds and runs all tests on every push.
- **Tests:** includes `NotificationDownTest` (booking and cancel succeed when the notification service is unreachable), plus gateway retry/timeout, mock idempotency, facade notification and `/health` tests.

### Configuration (environment variables)

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL` | `jdbc:mysql://localhost:3306/fitbook?...` | JDBC URL (compose points it at the `mysql` container) |
| `DB_USERNAME` / `DB_PASSWORD` | none (required) | MySQL credentials |
| `NOTIFICATION_BASE_URL` | `http://localhost:8080` | Where the gateway sends notifications |
| `NOTIFICATION_CONNECT_TIMEOUT_MS` | `1000` | Gateway connect timeout |
| `NOTIFICATION_READ_TIMEOUT_MS` | `1500` | Gateway read timeout |
| `NOTIFICATION_MAX_ATTEMPTS` | `3` | Total attempts per notification |
| `NOTIFICATION_RETRY_BACKOFF_MS` | `200` | Base backoff, doubled each retry |
| `NOTIFICATION_MOCK_FAIL` | `false` | Demo switch: mock service answers 503 |
| `NOTIFICATION_MOCK_DELAY_MS` | `0` | Demo switch: mock service delays its reply |

### Run locally

```powershell
cd fitbook
$env:DB_USERNAME = "fitbook_user"
$env:DB_PASSWORD = "your_password_here"
.\mvnw.cmd spring-boot:run
```

Then open http://localhost:8080 and log in with one of the seeded accounts below.

### Run with Docker

From the `fitbook/` folder (where `docker-compose.yml` lives):

```powershell
Copy-Item .env.example .env   # then set DB_USERNAME, DB_PASSWORD, MYSQL_ROOT_PASSWORD
docker compose up --build
```

`.env` is gitignored; never commit real credentials. Note that `schema.sql` recreates the tables on every app start, so data resets on restart even with the MySQL volume.

### Health and metrics

```powershell
curl http://localhost:8080/health
curl http://localhost:8080/actuator/metrics/fitbook.bookings.success
curl http://localhost:8080/actuator/metrics/fitbook.notifications.failed
curl http://localhost:8080/mock-notifications   # what the mock service has received
```

A counter appears in `/actuator/metrics` only after it has been used once.

### Failure demo (notification service down or slow)

Set a switch, restart the app, then book or cancel as a customer:

```powershell
$env:NOTIFICATION_MOCK_FAIL = "true"       # service answers 503
# or
$env:NOTIFICATION_MOCK_DELAY_MS = "5000"   # service is slower than the read timeout
.\mvnw.cmd spring-boot:run
```

The booking still succeeds. The log shows three failed attempts with the same key, then `Notification not delivered after 3 attempts, booking unaffected`, and `fitbook.notifications.failed` increases. Unset the variable (`Remove-Item Env:NOTIFICATION_MOCK_FAIL`) to restore normal behaviour.

### Tests and CI

```powershell
cd fitbook
.\mvnw.cmd verify
```

The tests need MySQL and the `DB_*` variables. CI (`.github/workflows/ci.yml`) starts a throwaway MySQL service container and runs `./mvnw -B verify` on every push.

### Video

Code walkthrough: _link to be added_

### Customer/Trainer Information

Email: kai.smith@example.com Password: customer1

Email: nya.smith@example.com Password: customer2

Email: lloyd.garmadon@example.com Password: trainer1

Email: cole.brookstone@example.com Password: trainer2
