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

### Code-walkthrough video

[Video link placeholder — add before submission]
