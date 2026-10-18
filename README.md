# AI Study Planner Agent

A full-stack study planning application for a 5th-semester B.E. CSE Agentic AI project. It combines a React/TypeScript UI, a Java 21 / Spring Boot 3 REST API, PostgreSQL + Flyway, deterministic timetable validation, and a bounded OpenAI tool-calling workflow. When no API key is configured, planning remains usable through a clearly labeled rule-based mode.

## Run locally on Windows

Requirements: PostgreSQL 16 or newer (PostgreSQL 18 is supported), Java 21, Maven 3.6.3+, and Node.js 20.19+ (or 22.12+). Docker is optional. Spring Boot runs Flyway migrations against the configured database; it does not drop tables.

### 1. Prepare PostgreSQL

Create a local database and login if you do not already have them. Run these commands in `psql` as a PostgreSQL administrator, changing the password to your own value:

```sql
CREATE ROLE planner LOGIN PASSWORD 'choose-a-local-password';
CREATE DATABASE study_planner OWNER planner;
```

If the role or database already exists, keep it and use its current credentials. Do not drop or recreate an existing database.

### 2. Start the backend

Open PowerShell in the project folder. Set these variables for the current terminal, using the credentials for your local PostgreSQL database:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/study_planner"
$env:DATABASE_USERNAME = "planner"
$env:DATABASE_PASSWORD = "your-local-database-password"
$env:DEMO_MODE = "true"
$env:APP_ORIGIN = "http://localhost:5173"
Set-Location .\backend
mvn spring-boot:run
```

The demo account is initialized only when `DEMO_MODE=true` and the account is absent:

- Email: `demo@planner.local`
- Password: `DemoPass123!`

The initializer leaves an existing account and its data untouched. The health endpoint is <http://localhost:8080/actuator/health>.

### 3. Start the frontend

Open a second PowerShell window in the project folder:

```powershell
Set-Location .\frontend
npm ci
npm run dev
```

Open <http://localhost:5173>. Vite proxies `/api` requests to port 8080 and includes the session and CSRF cookies.

The root `.env` file is optional; the commands above configure the backend directly and do not create or overwrite it. For local Spring Boot runs, set `OPENAI_API_KEY` in the backend PowerShell process before starting Spring Boot; Spring Boot does not load a root `.env` file automatically. Docker Compose reads the root `.env` file. Never put the key in a frontend environment file.

Docker Compose remains available as an optional local PostgreSQL demo environment. `scripts\start-demo.ps1` and `scripts\stop-demo.ps1` manage that optional stack.

## Before public deployment

This project is verified for local development and course-project use; it is not deployment-ready by default. Before exposing it publicly, use a managed PostgreSQL database with tested backups and restore procedures, set unique secrets through the deployment secret manager, configure `SESSION_COOKIE_SECURE=true` behind HTTPS, set `APP_ORIGIN` to the exact public frontend origin, disable demo seeding, restrict or remove public API documentation, and add rate limits for authentication and AI/PDF endpoints. Add centralized logs, metrics, alerting, and an incident/retention policy for student data. Do not expose the local database defaults or the optional demo workspace to public traffic.

## AI configuration

Set `OPENAI_API_KEY` in the backend process environment or root `.env`. The backend calls the OpenAI Chat Completions API with registered function tools, validates every result locally, and limits the tool loop. `OPENAI_MODEL` defaults to `gpt-4o-mini` and can be changed to a model available to your API account. If no key is set or the service fails, a deterministic planner runs and the UI identifies it as **Rule-based**.

## Study workspace

The signed-in workspace has a collapsible navigation rail and a saved light/dark theme. Use **My study plans** to select subjects, tune availability and revision preferences, generate a draft, review its scheduled sessions, and approve it. Use **Subjects & topics** to edit hierarchy, estimated time, importance, familiarity, and inclusion; use **Syllabus library** to extract and review PDF suggestions. The calendar supports week/month views and persists valid drag-to-move changes. Completing all learning sessions for a topic schedules available spaced-recall sessions.

Flyway V2 adds nullable/defaulted topic metadata and task session metadata without dropping or rebuilding existing tables. The migration runs when the backend starts. Back up a production database before any schema deployment, as part of your normal migration process.

## Windows helper

Run `scripts\start-demo.ps1` to start the Docker Compose stack and open the browser. Use `scripts\stop-demo.ps1` to stop the containers while preserving the database volume.

## Quality checks

```powershell
cd frontend
npm test
npm run build
cd ..\backend
mvn test
mvn package
```

## Project map

```text
frontend/       React, TypeScript, Vite, Tailwind CSS, Recharts, Lucide
backend/        Spring Boot REST API, security, JPA, scheduling, agent tools
docs/           Architecture, API examples, workflow and schema diagrams
scripts/        Windows demo start and stop helpers
docker-compose.yml
```

See [docs/architecture.md](docs/architecture.md) for the architecture, endpoints, constraints, and known limitations.

