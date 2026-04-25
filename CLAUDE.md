# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AuraC2 is a competitive programming contest platform with a Spring Boot backend, React/Vite frontend, PostgreSQL database, and RabbitMQ message broker. Code execution is delegated to Judge0 via asynchronous callbacks.

## Development Commands

### Infrastructure (required first)
```bash
docker-compose up -d                    # Start PostgreSQL + RabbitMQ
docker-compose down -v                  # Full reset (deletes data)
```

### Backend (Spring Boot 3.4.2, Java 21)
```bash
cd backend/aura-contest-control/jwtAuthServer
./mvnw spring-boot:run                  # Runs on :8080
./mvnw clean package                    # Build JAR
```

### Frontend (React 18, Vite 6, TypeScript)
```bash
cd UI
npm install
npm run dev                             # Runs on :5173, proxies /auth and /api to :8080
npm run build
```

### Judge0 Callbacks (local dev)
Judge0 needs a public URL to send callbacks. Use ngrok and update `judge0.callback` in `application.yml`:
```bash
ngrok http 8080
```

### API Documentation
Swagger UI available at http://localhost:8080/swagger-ui.html when backend is running.

## Architecture

### Three Backend Domains
The backend (`backend/aura-contest-control/jwtAuthServer/src/main/java/com/server/contestControl/`) is split into three domain packages:

- **authServer** — JWT access tokens (15min) + refresh token rotation via HttpOnly cookies. Custom `JwtAuthFilter` in the Spring Security filter chain. Roles: ADMIN, TEAM.
- **contestServer** — Contest lifecycle (UPCOMING → RUNNING → PAUSED → ENDED). `ContestStatusSyncService` runs a scheduler to sync persisted status with effective state based on time.
- **submissionServer** — Async submission pipeline: API saves to DB → publishes ID to RabbitMQ `submissionQueue` → `SubmissionConsumer` sends each test case to Judge0 → Judge0 calls back `/api/callback/judge0/{submissionId}/{testCaseNumber}` → `CallbackHandler` updates verdict.

### Frontend Structure
`UI/src/` has three top-level modules:
- **admin/** — Contest management dashboard (sidebar nav, switches views via local state)
- **team/** — Problem workspace with code editor and submission history
- **auth/** — Login page
- **services/** — API clients (`authApi.ts`, `contestApi.ts`, etc.)

Root `App.tsx` acts as a router: renders LoginPage, AdminApp, or TeamApp based on JWT token + decoded role. No React Router — all navigation is conditional rendering.

### Key Patterns
- **Auth**: Access token in localStorage, refresh token in HttpOnly cookie. Frontend auto-refreshes on 401.
- **Authorization**: `@PreAuthorize` annotations on controllers. ADMIN for contest/problem management, TEAM/ADMIN for submissions.
- **State management**: Local React state only (useState hooks), no Redux/Zustand.
- **UI components**: Radix UI primitives + Tailwind CSS v4 + class-variance-authority for variants.
- **Notifications**: sonner for toast messages.

### Submission Verdict Flow
PENDING → RUNNING → one of: ACCEPTED, WRONG_ANSWER, TLE, COMPILATION_ERROR, RUNTIME_ERROR, INTERNAL_ERROR. First failing test case short-circuits to failure; all passing marks ACCEPTED.

## Configuration

- **Backend config**: `backend/aura-contest-control/jwtAuthServer/src/main/resources/application.yml`
- **DB**: PostgreSQL on :5432, database `authserver`. DDL is `create-drop` (dev only).
- **RabbitMQ**: :5672 (AMQP), :15672 (management UI). Queues: `submissionQueue`, `resultQueue`.
- **Vite proxy**: `/auth` and `/api` routes proxied to localhost:8080 in `UI/vite.config.ts`.

## Unimplemented Features

Scoreboard/ranking, clarifications/announcements, real-time WebSocket updates, and per-test-case result table are not yet implemented. Result queue consumer is empty.
