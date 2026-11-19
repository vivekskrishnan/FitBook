# FitBook: AI-Powered Appointment Scheduling

FitBook is an online appointment scheduling system for a fitness studio, built as the CMPE 172 term project (Enterprise Software). Customers browse trainer availability and book or cancel sessions; trainers publish their own slots. On top of a conventional layered Spring Boot backend, **Milestone 4 adds three enterprise AI features built with Spring AI and Google Gemini**, including two agents that act on the user's behalf and on their own. The same milestone adds a **JWT-secured REST API** and a **Next.js (React, TypeScript) frontend**.

## AI and agentic AI highlights

| Feature | What it does | Where |
|---------|--------------|-------|
| **Grounded Q&A (RAG)** | Answers studio questions (hours, prices, cancellation policy) by retrieving the relevant sections of a knowledge file from a vector store and answering *only* from them, with the sources cited. | `ai/rag/` |
| **Agent 1: conversational booking assistant** | A chat agent with tool calling and per-user memory. It finds open slots, lists appointments, and books or cancels, but only after the customer confirms. | `ai/agent1/` |
| **Agent 2: autonomous reminder agent** | A scheduled job (no user prompt) that finds upcoming appointments, has the LLM write a personalized reminder for each, and sends it through the notification service. | `ai/agent2/` |

Design points worth reading the code for:

- **The model never writes to the database.** Agents only call a small set of Java tools, and writes go through the same transactional `BookingService` as the website, so the double-booking guard still applies.
- **Confirm-before-write is enforced in code**, not just in the prompt: a booking or cancellation is proposed in one chat turn and can only be confirmed in a later one.
- **The agent acts as the logged-in user.** The user id comes from the login session and is never a tool parameter, so the model cannot act for anyone else.
- **Resilience:** every LLM call retries with exponential backoff, and the reminder agent falls back to a template message if the LLM is down.
- **Tests never call Gemini:** the chat model and vector store are mocked (127 automated tests across backend and frontend).


## Frontend and API security highlights

- **Next.js 16 frontend** (`frontend/`, React 19, TypeScript): browse slots, log in, book and cancel, trainer dashboard with slot management and the reminder button, and the AI assistant (chat plus FAQ).
- **JWT authentication** (`/api/auth/login`): Spring Security's resource server verifies HS256-signed tokens (signature, expiry and issuer). The token carries the user id, name, email and role; role checks work the same as for the session login.
- **The token never reaches browser JavaScript.** The Next.js server acts as a backend-for-frontend proxy: it stores the JWT in an `httpOnly`, `SameSite=Lax` cookie, removes it from the login response, and attaches it as a `Bearer` header when forwarding `/api/*` calls to Spring Boot. This protects the token from XSS theft.
- **No secret in the repo.** The signing key comes from `JWT_SECRET` (minimum 32 characters); if it is unset a random key is generated at startup.
- The original Thymeleaf pages and session login are still served by the backend on port 8080 alongside the new API.

## Milestones

| Milestone | Focus | Report | Demo |
|-----------|-------|--------|------|
| 1 | Requirements, design, layered skeleton (Controller, Service, Repository, hand-written SQL, MySQL) | _link to be added_ | _link to be added_ |
| 2 | Authentication and roles (customer / trainer), booking, cancellation, provider slot management, double-booking protection | _link to be added_ | _link to be added_ |
| 3 | Integration, distribution and deployment: notification gateway with timeouts, retries, idempotency and fallback; logging, health and metrics; Docker and GitHub Actions CI | _link to be added_ | _link to be added_ |
| 4 | Enterprise AI: RAG Q&A, conversational agent, autonomous reminder agent; JWT-secured REST API and Next.js frontend; tests | _link to be added_ | _link to be added_ |

## Tech stack

- Java 21, Spring Boot 4.1.1 (Spring Web MVC, Spring JDBC, Spring Security, Thymeleaf, Actuator)
- Spring AI 2.0.2 with Google Gemini (chat and embeddings), in-memory `SimpleVectorStore`
- Spring Security OAuth2 resource server for JWT (HS256) on the REST API
- Next.js 16, React 19, TypeScript, Vitest (in `frontend/`)
- MySQL 8+ (hand-written SQL over `JdbcTemplate`, no ORM)
- Maven wrapper, Docker and Docker Compose, GitHub Actions

## Quick start

### Prerequisites

- JDK 21 and a running MySQL server on `localhost:3306`.
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/) (only needed for the AI features).

### 1. Create the database and user

```sql
CREATE DATABASE fitbook;
CREATE USER 'fitbook_user'@'localhost' IDENTIFIED BY 'your_password_here';
GRANT ALL PRIVILEGES ON fitbook.* TO 'fitbook_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Configure

Copy `fitbook/.env.example` to `fitbook/.env` and fill it in (the file is gitignored; never commit real credentials). The app reads it automatically, and real environment variables take precedence.

```
DB_USERNAME=fitbook_user
DB_PASSWORD=your_password_here
GEMINI_API_KEY=your_gemini_api_key
```

### 3. Run

```powershell
cd fitbook
.\mvnw.cmd spring-boot:run
```

(macOS/Linux: `./mvnw spring-boot:run`.) The app starts on **http://localhost:8080**.

`schema.sql` creates the tables only if they are missing and `seed.sql` adds the sample rows only if they are missing, so **data survives restarts**. To start from a clean database, drop and recreate it (`DROP DATABASE fitbook; CREATE DATABASE fitbook;`).

### 4. Run the Next.js frontend (optional, needs Node 22+)

With the backend running on port 8080:

```powershell
cd frontend
npm install
npm run dev          # http://localhost:3000
```

The frontend forwards `/api/*` to `BACKEND_URL` (default `http://localhost:8080`; see `frontend/.env.example`). For a production build run `npm run build` then `npm start`. Set `JWT_SECRET` (32+ characters) for the backend if you want logins to survive backend restarts.

### Demo accounts (seed data)

| Role | Email | Password |
|------|-------|----------|
| Customer | kai.smith@example.com | customer1 |
| Customer | nya.smith@example.com | customer2 |
| Trainer | lloyd.garmadon@example.com | trainer1 |
| Trainer | cole.brookstone@example.com | trainer2 |

These are sample accounts for the demo database only.

### Run with Docker

From the `fitbook/` folder:

```powershell
Copy-Item .env.example .env   # then set DB_USERNAME, DB_PASSWORD, MYSQL_ROOT_PASSWORD, GEMINI_API_KEY, JWT_SECRET
docker compose up --build
```

This starts three containers: MySQL (named volume), the Spring Boot app on **http://localhost:8080** (Thymeleaf UI and API), and the Next.js frontend on **http://localhost:3000**. Data persists across `docker compose down` and `up`; use `docker compose down -v` to wipe it.

## REST API and JWT authentication (Milestone 4)

Log in with `POST /api/auth/login` (`{"email": "...", "password": "..."}`) to get `{"token": "...", "expiresInSeconds": 3600, "user": {...}}`, then send `Authorization: Bearer <token>` on every other call. The API is stateless (no session, no cookies, so no CSRF risk) and returns JSON errors.

| Endpoint | Access | Purpose |
|----------|--------|---------|
| `POST /api/auth/login` | public | Exchange email and password for a JWT (a wrong email and a wrong password give the same 401) |
| `GET /api/auth/me` | any user | The user in the token |
| `GET /api/slots`, `GET /api/slots/options` | public | Browse open slots (filters: `trainerId`, `serviceId`, `date`, `page`) |
| `GET /api/appointments` | customer | My appointments |
| `POST /api/appointments` `{"slotId": 3}` | customer | Book a slot (`409` if it was taken) |
| `POST /api/appointments/{id}/cancel` | customer | Cancel my appointment |
| `GET/POST /api/provider/slots`, `DELETE /api/provider/slots/{id}`, `GET /api/provider/bookings` | trainer | Manage my slots, see my bookings |
| `GET /api/ai/ask?q=` | any user | FAQ question answering (RAG) |
| `POST /api/ai/chat` `{"message": "..."}` | customer | Booking assistant (Agent 1) |
| `POST /api/ai/reminders/run?hours=24` | trainer | Run the reminder agent now (Agent 2) |

Booking and cancelling use the same `AppointmentFacade` and transactional `BookingService` as the website and the AI agent, so ownership checks, the double-booking guard and notifications are identical everywhere.

| Variable | Default | Purpose |
|----------|---------|---------|
| `JWT_SECRET` | random per start | HS256 signing key, at least 32 characters. Set it in production and Docker so tokens survive restarts. |
| `JWT_EXPIRATION_MINUTES` | `60` | Token lifetime |

## Milestone 4: Enterprise AI

### Using the AI features

| Feature | How to use it | Who |
|---------|---------------|-----|
| FAQ Q&A | `GET /ai/ask?q=What is the cancellation policy?` (while logged in) returns `{"answer": ..., "sources": [...]}` | any logged-in user |
| Booking assistant | Log in as a customer, open **My Appointments**, click **AI assistant** (`/ai/chat`). Try "Book the earliest Pilates slot", then reply "yes". | customers |
| Reminder agent | Runs hourly by itself. For the demo, log in as a trainer and click **Send reminders now** on the dashboard (`POST /ai/reminders/run?hours=24`). | trainers |

### How it fits together

```
ai/
├── LlmRetry.java        retry with exponential backoff for Gemini 503/429 (shared)
├── rag/                 KnowledgeIngestor (FAQ -> vector store), KnowledgeService (retrieve + answer), RagController
├── agent1/              BookingTools (@Tool methods), PendingActionStore (confirm-before-write),
│                        ChatAgentService (prompt + memory + tools), ChatController, chat page
└── agent2/              ReminderAgent (find, write with LLM, send, fall back), ReminderScheduler (@Scheduled),
                         ReminderController (manual trigger)
```

- **Knowledge source:** `fitbook/src/main/resources/knowledge/fitbook-faq.md` (one `##` section per policy; one section is one embedded document).
- **Writes are guarded by the service layer:** the agent's `confirmPendingAction` tool calls `AppointmentFacade.book()/cancel()`, which run in the transactional `BookingService` (row lock on the slot, `UNIQUE(slot_id)` in the schema).
- **Access control:** `/ai/chat` is customers only, `/ai/reminders/**` is trainers only, `/ai/ask` needs a login. The POST endpoints require a CSRF token.

### AI configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `GEMINI_API_KEY` | `missing-key` | Google AI Studio key. Set **only** the API key; do not set a project id or location, which switches Spring AI to Vertex mode and rejects free keys. |
| `GEMINI_CHAT_MODEL` | `gemini-3.8-flash` | Chat model. The free tier limits each model separately (about 20 requests per day), so switch models if you hit `429`. |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-001` | Embedding model for the FAQ. |
| `AI_MAX_ATTEMPTS` / `AI_RETRY_BACKOFF_MS` | `4` / `1500` | LLM retry count and base backoff (doubles each retry). |
| `REMINDERS_ENABLED` | `true` | Turn the scheduled reminder job on or off. |
| `REMINDERS_WINDOW_HOURS` | `24` | Remind appointments starting within this many hours. |
| `REMINDERS_INTERVAL_MS` / `REMINDERS_INITIAL_DELAY_MS` | `3600000` / `300000` | Job interval and first-run delay. |

### Demo of the reminder agent

Seed appointments are in the future relative to the first start, but to demo a reminder that is due now, create an appointment starting within 24 hours:

```sql
INSERT INTO availability_slots (trainer_id, service_id, start_time, end_time, status)
  VALUES (1, 1, NOW() + INTERVAL 2 HOUR, NOW() + INTERVAL 3 HOUR, 'BOOKED');
INSERT INTO appointments (slot_id, customer_id, service_id, status)
  VALUES (LAST_INSERT_ID(), 1, 1, 'BOOKED');
```

Then log in as a trainer and click **Send reminders now**. The reminder appears at `http://localhost:8080/mock-notifications`.

## Notification gateway, health and metrics (Milestone 3)

- A mock notification service (`POST /mock-notifications`) is reached only over HTTP through a `NotificationGateway` client. The gateway uses connect/read timeouts, bounded retries with backoff, and a fallback, so a slow or down notification service never fails a booking.
- Bookings and cancellations are **commit-then-notify**: the database transaction commits first, then the confirmation is sent. Retries reuse an idempotency key (`BOOKED-<id>`, `CANCELLED-<id>`, `REMINDER-<id>`); a repeated key is answered `DUPLICATE`.
- `GET /health` checks MySQL (200 UP / 503 DOWN). Micrometer metrics at `/actuator/metrics/<name>`: `fitbook.bookings.success`, `fitbook.bookings.failed`, `fitbook.booking.latency`, `fitbook.notifications.sent`, `fitbook.notifications.failed`.

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL` | local MySQL | JDBC URL (Docker Compose points it at the `mysql` container) |
| `NOTIFICATION_BASE_URL` | `http://localhost:8080` | Where the gateway sends notifications |
| `NOTIFICATION_CONNECT_TIMEOUT_MS` / `NOTIFICATION_READ_TIMEOUT_MS` | `1000` / `1500` | Gateway timeouts |
| `NOTIFICATION_MAX_ATTEMPTS` / `NOTIFICATION_RETRY_BACKOFF_MS` | `3` / `200` | Gateway retries |
| `NOTIFICATION_MOCK_FAIL` / `NOTIFICATION_MOCK_DELAY_MS` | `false` / `0` | Demo switches: make the mock service fail or respond slowly |

Failure demo: set `NOTIFICATION_MOCK_FAIL=true`, restart, and book. The booking still succeeds; the log shows three failed attempts and `fitbook.notifications.failed` increases.

## Tests and CI

```powershell
cd fitbook
.\mvnw.cmd verify
```

**127 automated tests: 104 backend (JUnit) and 23 frontend (Vitest).** The backend tests need MySQL and the `DB_*` variables; none call Gemini.

- Booking, cancellation, concurrent double-booking, notification gateway and failure handling (Milestone 2 and 3).
- RAG: chunking, retrieval into the prompt, sources, lazy ingestion and retry.
- Agent 1: tools, confirm-before-write, double-booking through the agent, cancel flow, memory per user.
- Agent 2: reminders with a mocked LLM, fallback, de-duplication, retry of failed delivery.
- HTTP and security: role checks, CSRF, blank input, no leaking of internal errors.
- JWT API: login, identical errors for wrong password and unknown email, tampered, expired, wrong-key and wrong-issuer tokens rejected, role rules, booking, double-booking (409), ownership on cancel, the customer id coming from the token and not the request body.
- Frontend (`cd frontend; npm test`): the login proxy keeps the token in an httpOnly cookie and out of the response body, Bearer header injection, expired-token cookie clearing, logout, path-traversal rejection, and no leaking of backend errors; date and price formatting.

CI (`.github/workflows/ci.yml`) has two jobs on every push and pull request: the backend job starts a throwaway MySQL container and runs `./mvnw -B verify`; the frontend job runs `npm ci`, type check, unit tests and a production build.

## Verification results

Checked against the Docker deployment (`docker compose up --build`) and the local run:

| Check | Result |
|-------|--------|
| Booking a slot | Works; redirects to the confirmation page and the row is saved |
| Cancelling an appointment | Works; status becomes `CANCELLED` |
| Double-booking protection | Two customers booking the same slot, including simultaneously: exactly one succeeds, the other is told the slot is unavailable; the database ends with one appointment per slot |
| Data persistence after restart | Works after the fix described below: a booking survives an app restart and a full `docker compose down` / `up` |
| Tests | Backend 104 / 104 and frontend 23 / 23 pass; the backend suite was run repeatedly against a persistent database |
| CI | The backend CI job succeeded on the latest pushes to `main`; the new frontend job has not run on GitHub yet |
| Docker deployment | MySQL, app and frontend containers all start on a fresh volume; `/health` is UP; through the Next.js container: login, booking, trainer view of the booking, AI FAQ answer via JWT, logout; a booking and the login cookie both survived an app restart |
| JWT and Next.js | Login sets an httpOnly cookie and the token is absent from the response body; protected calls without a valid cookie get 401, wrong-role calls get 403, a double-booking gets 409 |
| AI features (live Gemini) | All three were run end to end against Gemini: `/ai/ask` gave a grounded answer with sources and said "I don't have that information" for an out-of-scope question; the chat agent proposed, confirmed and listed a booking; the reminder agent sent an AI-written reminder |

**Docker fix:** the MySQL health check used a local socket, which reports healthy during first-time database setup before TCP connections work, so the app could start too early on a fresh volume and fail. It now checks over TCP (`-h 127.0.0.1`).

**Persistence fix:** originally `schema.sql` dropped every table and `seed.sql` deleted all rows on each startup, so all bookings were lost on restart. Schema creation is now `CREATE TABLE IF NOT EXISTS` and seeding uses `INSERT IGNORE`; seed slots are dated relative to the first start.

## Known limitations

- **Frontend:** the Next.js UI was verified through the HTTP proxy and its unit tests, and it builds and runs in Docker, but I did not review it visually in a browser (the automation extension was unavailable). Expect rough edges in layout. It has no end-to-end browser tests.
- **JWT:** tokens are not revocable before they expire (logout only clears the cookie), there is no refresh token, and the login endpoint has no rate limiting or lockout. One shared HS256 secret signs and verifies; fine for a single service, but a larger system would use asymmetric keys.
- **Two UIs:** the original Thymeleaf pages (session login) and the Next.js frontend (JWT) both exist and use the same services. Their sign-ins are independent.
- **Gemini free tier:** each model allows only about 20 requests per day, and models are sometimes overloaded (503). When a model is out of quota, `/ai/ask` and `/ai/chat` return a generic 503 after retrying (this can take about a minute), and the reminder agent falls back to template messages. Switch `GEMINI_CHAT_MODEL`.
- **In-memory AI state:** the vector store, the chat memory (last 20 messages per user), pending proposals (10-minute expiry) and the "already reminded" set are lost on restart. The FAQ is re-embedded on the first question after a restart.
- **Sources are what was retrieved, not what was used:** `/ai/ask` lists the retrieved FAQ sections even when the model answers "I don't have that information".
- **AI correctness is model-dependent:** unit tests mock the model, so they prove our code's behavior, not the model's judgment. Smaller models sometimes need a repeated "yes".
- **Cancelled slots are not reopened:** `UNIQUE(slot_id)` ties one appointment row to a slot, so a cancelled slot stays unavailable.
- **Notifications are mocked:** the notification service is a mock inside the same app; nothing is emailed or texted.
- **Existing databases:** if you already have a `fitbook` database from an older version, its seed slots keep their old dates. Drop and recreate the database to get fresh seed data.
- **Deployment:** Docker Compose is a single-host setup with development credentials supplied through `.env`.

## Project documents

- [`AI_CODE_GUIDE.md`](AI_CODE_GUIDE.md): walkthrough of the AI code, for readers and for the demo video.
- [`VIDEO_SCRIPT.md`](VIDEO_SCRIPT.md): outline for the code walkthrough video.
- [`MILESTONE4_REPORT_DRAFT.md`](MILESTONE4_REPORT_DRAFT.md): draft of the Milestone 4 report.
- [`plan.md`](plan.md): implementation plan and checklist for Milestone 4.
