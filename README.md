# AuraC² Backend — Online Programming Contest Control System

AuraC² is the backend of an online programming contest platform designed for university-level competitive programming contests.  
It provides the core infrastructure for:

- authentication and role-based access control
- contest lifecycle management
- problem and test case management
- asynchronous code submission processing
- Judge0-based remote execution
- persistent submission tracking

The system is designed to grow beyond a simple CRUD backend into a real contest-control platform with clear separation between authentication, contest management, and submission execution pipelines.

---

## Overview

This backend is organized into three main domains:

### 1. Authentication Server
Handles:
- admin-protected team registration
- login
- JWT access token generation
- refresh token rotation
- logout
- role-based authorization

### 2. Contest Server
Handles:
- contest creation
- contest status transitions
- problem management
- test case management
- admin user management

### 3. Submission Server
Handles:
- code submissions
- RabbitMQ-based asynchronous dispatch
- Judge0 execution requests
- callback processing
- verdict persistence

---

## Current Feature Status

| Feature | Status |
|---|---|
| User registration | Implemented |
| Login with JWT access token | Implemented |
| Refresh token via cookie | Implemented |
| Logout + token revocation | Implemented |
| Role-based authorization | Implemented |
| Contest creation and status transitions | Implemented |
| Problem management | Implemented |
| Test case management | Implemented |
| Submission entity and persistence | Implemented |
| RabbitMQ submission dispatch | Implemented |
| Judge0 submission sending | Implemented |
| Judge0 callback handling | Implemented |
| Per-test-case final aggregated tracking | Implemented for judge runs |
| ICPC-style scoreboard / ranking | Implemented |
| Real-time scoreboard SSE | Implemented |
| Scoreboard freeze / reveal | Implemented |
| Clarifications | Implemented |
| Announcements | Not implemented yet |
| Real-time contest updates | Implemented |

---

## Architecture

The codebase is split into domain-oriented packages:

```text
src/main/java/com/server/contestControl
├── authServer
│   ├── config
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── enums
│   ├── exception
│   ├── filter
│   ├── repository
│   ├── service
│   └── util
│
├── contestServer
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── enums
│   ├── repository
│   ├── scoreboard
│   └── service
│
├── submissionServer
│   ├── config
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── enums
│   ├── queue
│   │   ├── submission
│   │   └── result
│   ├── repository
│   ├── service
│   └── util
│
└── JwtAuthServerApplication.java
```

This separation makes the project easier to reason about as it grows:
- **authServer** owns identity and token logic
- **contestServer** owns contest-related business rules
- **contestServer.scoreboard** owns deterministic ICPC scoring, freeze/reveal state, and scoreboard SSE
- **submissionServer** owns asynchronous judging flow

## Real-Time Scoreboard

AuraC2 now includes a real-time ICPC-style scoreboard with admin and public/team views.

- Public/team snapshot: `GET /api/scoreboard/contests/{contestId}`
- Admin snapshot: `GET /api/admin/scoreboard/contests/{contestId}`
- Public/team stream: `GET /api/scoreboard/contests/{contestId}/stream`
- Admin stream: `GET /api/admin/scoreboard/contests/{contestId}/stream`
- Admin reveal endpoints: `/api/admin/scoreboard/contests/{contestId}/reveal/*`

Every finalized submission publishes a scoreboard domain event. Accepted submissions immediately recalculate solved counts, penalties, first-to-solve cells, ranks, and row-level SSE updates. Public/team streams respect freeze and reveal state; admin streams remain live.

Contest timing and scoreboard visibility are pause-aware. Submissions are accepted only while an effective `RUNNING` contest exists; upcoming, paused, and ended contests reject new submissions, and the exact effective end instant is treated as ended. ICPC penalties count wrong attempts before the first accepted submission according to the configured contest penalty, while wrong attempts after the first accepted submission do not add penalty. Public frozen scoreboards hide cells at or after the freeze boundary until reveal; admin views remain live.

Full API, SSE, scoring, reveal, testing, and migration details are in [`docs/scoreboard-feature-documentation.md`](docs/scoreboard-feature-documentation.md).

---

## Core Domain Model

### User
Represents a platform user authenticated through Spring Security.

Important fields:
- `id`
- `username`
- `password`
- `role`
- refresh token collection

Current roles include:
- `ADMIN`
- `TEAM`

### RefreshToken
Stored in the database instead of trusting refresh tokens blindly on the client side.

Important fields:
- token hash
- device IP
- creation / expiration timestamps
- revoked flag
- owning user

This allows:
- revocation
- rotation
- stronger control over session lifecycle

### Contest
Represents a programming contest.

Important fields:
- title
- description
- start time
- duration
- status

Current contest statuses:
- `UPCOMING`
- `RUNNING`
- `PAUSED`
- `ENDED`

### Problem
Represents a contest problem linked to a specific contest.

Important fields:
- title
- description
- time limit
- memory limit
- difficulty

### TestCase
Represents an input/output pair linked to a problem.

Important fields:
- input data
- expected output
- visibility flag (`isPublic`)

### Submission
Represents a participant submission.

Important fields:
- contest
- problem
- user
- source code
- language
- verdict
- execution time
- memory usage
- creation timestamp

Current verdict flow begins with `PENDING`, then moves through asynchronous execution states such as `RUNNING`, and finally reaches a final verdict.

---

## Authentication Flow

The authentication design is more mature than a basic “login and return token” flow.

### Registration
A user can register with:
- username
- password
- role

The password is encoded before persistence.

### Login
On successful login:
- credentials are validated
- an access token is generated
- a refresh token is generated
- the refresh token is hashed and stored in the database
- the refresh token is returned in an HTTP cookie

### Refresh
When the client requests token refresh:
- the refresh token is extracted from the cookie
- it is validated
- ownership and revocation are checked
- the old refresh token is revoked
- a new access token + refresh token pair is issued

This gives you **refresh token rotation**, which is stronger than naive long-lived session handling.

### Logout
On logout:
- the refresh token is extracted
- validated
- revoked
- removed from the cookie
- security context is cleared

---

## Security Model

Security is implemented with Spring Security using a custom JWT filter.

### Public routes
The following categories are exposed publicly:
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- Swagger / OpenAPI documentation
- `GET /api/contest/active`, `/upcoming`, `/paused`, and `/ended`
- public scoreboard snapshot and stream routes under `/api/scoreboard/**`
- public answered clarifications under `/api/clarifications/public/**`
- Judge0 callback routes under `/api/callback/judge0/**`

`POST /auth/register` is not public. It is protected by the security filter chain and by method-level `ADMIN` authorization, and the JWT filter intentionally processes bearer tokens on that route.

### Protected routes
Role restrictions include:
- contest mutation endpoints → `ADMIN`
- problem/test case creation, update, and delete → `ADMIN`
- submissions → `TEAM` or `ADMIN`
- admin user management → `ADMIN`
- admin scoreboard/reveal controls → `ADMIN`
- clarification submission/my routes → `TEAM`
- clarification admin reply/review routes → `ADMIN`

The application is stateless:
- CSRF disabled
- form login disabled
- HTTP basic disabled
- session creation policy set to `STATELESS`

### Route authorization map

| Endpoint group | Access policy |
|---|---|
| `POST /auth/login`, `/auth/refresh`, `/auth/logout` | Public authentication/session endpoints; refresh/logout use the HTTP-only refresh cookie. |
| `POST /auth/register` | `ADMIN` only; bearer JWT is processed before method security. |
| `GET /api/contest/active`, `/upcoming`, `/paused`, `/ended` | Public contest read endpoints. |
| `/api/contest/**` mutation routes and `/api/contest/stream` | `ADMIN` only. |
| `GET /api/problems/**` | `TEAM` or `ADMIN`. |
| `POST`, `PUT`, `DELETE /api/problems/**` | `ADMIN` only. |
| `GET /api/testcases/**` | `TEAM` or `ADMIN`; service filters private cases for teams. |
| `POST`, `PUT`, `DELETE /api/testcases/**` | `ADMIN` only. |
| `/api/submissions/**` | `TEAM` or `ADMIN`; team access to submission details is owner-checked in the service. |
| `/api/admin/**` | `ADMIN` only, including user management, rejudge, and admin scoreboard controls. |
| `/api/scoreboard/**` | Public scoreboard snapshot and stream. |
| `/api/clarifications/public/**` | Public answered clarifications. |
| `/api/clarifications/my/**`, `POST /api/clarifications` | `TEAM` only. |
| `/api/clarifications/admin/**` | `ADMIN` only. |
| `/api/callback/judge0/**` | Externally reachable callback endpoint; requests must pass HMAC signature verification before state changes. |

### no-security profile

The `no-security` profile disables the main security filter chain and is only allowed together with a local development or test profile. Startup fails if `no-security` is active by itself or with `prod`.

---

## Submission Pipeline

This is the most important architectural part of the system.

AuraC² does not execute code synchronously inside the request thread.  
Instead, it uses **RabbitMQ** to decouple submission intake from execution.

### Flow

1. A user submits source code through `/api/submissions`
2. The submission is saved in PostgreSQL
3. After the database transaction commits, the submission ID is published to RabbitMQ
4. A RabbitMQ consumer loads the submission
5. All test cases for the problem are fetched
6. Each test case is sent to Judge0 asynchronously
7. Judge0 calls back the backend for each test case result
8. The backend updates the submission verdict, execution time, and memory usage

This design prevents the API from blocking while external execution is happening.

---

## Why RabbitMQ is justified here

RabbitMQ is not used as decoration.  
It solves a real architectural problem in contest systems.

### Without RabbitMQ
The request thread would need to:
- receive the code
- send execution requests
- wait on external processing
- manage execution timing issues

That becomes fragile and hard to scale.

### With RabbitMQ
The backend can:
- persist the submission immediately
- return quickly
- process execution asynchronously
- isolate execution pressure from API responsiveness
- prepare for future scaling into separate workers

Even on one server, this is still a meaningful design because asynchronous judging is a naturally queued workload.

---

## Judge0 Integration

Judge0 integration is already present in the uploaded source.

### Current behavior
For each test case, the backend sends:
- source code
- mapped language ID
- stdin
- expected output
- signed callback URL

Judge0 then calls back:

```text
/api/callback/judge0/{submissionId}/{judgeRunId}/{testCaseNumber}?signature=...
```

The callback handler:
- verifies the HMAC signature before changing submission state
- resolves the submission
- maps Judge0 status to internal verdict
- stores execution time and memory usage
- stores one result per submission, judge run, and test case
- waits until all test case callbacks for the current judge run are received
- calculates the final verdict from the completed run, so out-of-order callbacks cannot mark a submission accepted early

### Important note
The current implementation is functional but still early-stage.  
It uses Judge0 `expected_output` comparison only; normalized comparison, floating-point tolerance, custom checkers, generated tests, and interactive judging are not implemented.

That means the system already supports real execution flow, but there is still room to evolve toward more detailed judging analytics.

---

## REST API Summary

### Authentication

#### Register
```http
POST /auth/register
```

Example body:
```json
{
  "username": "team1",
  "password": "123456"
}
```

This route is for administrators creating TEAM accounts. Anonymous users and TEAM users cannot register accounts.

#### Login
```http
POST /auth/login
```

Example body:
```json
{
  "username": "team1",
  "password": "123456"
}
```

#### Refresh token
```http
POST /auth/refresh
```

Uses the refresh token from cookie.

#### Logout
```http
POST /auth/logout
```

---

### Contest Management

#### Create contest
```http
POST /api/contest
```

#### Start contest
```http
PUT /api/contest/{id}/start
```

#### Pause contest
```http
PUT /api/contest/{id}/pause
```

#### End contest
```http
PUT /api/contest/{id}/end
```

#### Get running contest
```http
GET /api/contest/active
```

#### Get upcoming contest
```http
GET /api/contest/upcoming
```

#### Get paused contest
```http
GET /api/contest/paused
```

#### Get ended contests
```http
GET /api/contest/ended
```

---

### Problem Management

#### Create problem
```http
POST /api/problems
```

#### Get one problem
```http
GET /api/problems/{id}
```

#### Get all contest problems
```http
GET /api/problems/contest/{id}
```

---

### Test Case Management

#### Add test case
```http
POST /api/testcases/{problemId}
```

#### Get test cases for a problem
```http
GET /api/testcases/problem/{problemId}
```

---

### Submission Management

#### Submit code
```http
POST /api/submissions
```

#### Get one submission
```http
GET /api/submissions/{id}
```

#### Get my submissions for a problem
```http
GET /api/submissions/my?problemID={id}
```

#### Get all my submissions
```http
GET /api/submissions/my/all
```

---

### Admin Endpoints

#### Get all users
```http
GET /api/admin/users
```

#### Update username
```http
PUT /api/admin/users/{userId}/name
```

#### Update password
```http
PUT /api/admin/users/{userId}/password
```

#### Delete user
```http
DELETE /api/admin/users/{userId}
```

#### Get all submissions
```http
GET /api/admin/users/submissions
```

### Admin Rejudge Endpoints

All rejudge endpoints require the `ADMIN` role. Rejudge reuses the normal RabbitMQ submission queue and Judge0 callback flow.

#### Rejudge selected submissions
```http
POST /api/admin/rejudge/submissions
```

Example body:
```json
{
  "submissionIds": [1, 2, 3]
}
```

#### Rejudge all submissions for a problem
```http
POST /api/admin/rejudge/problem/{problemId}
```

#### Rejudge all submissions in a contest
```http
POST /api/admin/rejudge/contests/{contestId}
```

---

## Configuration

The uploaded source includes configuration for:
- PostgreSQL
- RabbitMQ
- Gmail SMTP
- JWT secrets
- Judge0 endpoint
- Swagger UI

### Example application.yml shape

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/authserver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  rabbitmq:
    host: ${RABBIT_HOST}
    port: ${RABBIT_PORT}
    username: ${RABBIT_USERNAME}
    password: ${RABBIT_PASSWORD}

  mail:
    host: smtp.gmail.com
    port: 587
    username: ${SMTP_EMAIL}
    password: ${SMTP_PASS}

jwt:
  access-secret: ${JWT_ACCESS_SECRET}
  refresh-secret: ${JWT_REFRESH_SECRET}
  expiration: 900000
  refresh-expiration: 604800000

judge0:
  url: ${JUDGE0_URL}
  callback: ${JUDGE0_CALLBACK_URL}
  callback-secret: ${JUDGE0_CALLBACK_SECRET}
```

---

## Important Security Note

The uploaded `application.yml` contains real-looking secrets and credentials.  
These should **not** remain committed in a public repository.

Move them to:
- environment variables
- `.env`
- Docker secrets
- deployment platform secret storage

At minimum, rotate:
- database password
- SMTP app password
- JWT secrets
- any exposed public callback URLs

---

## Running the Project

### Requirements
- Java 17+
- PostgreSQL
- RabbitMQ
- Maven
- internet access to reach Judge0
- a public callback URL for Judge0 responses during local development

### Start RabbitMQ with Docker
```bash
docker run -d \
  --hostname rabbit \
  --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

RabbitMQ dashboard:
```text
http://localhost:15672
```

Default credentials:
```text
guest / guest
```

### Expose local callback endpoint
Because Judge0 needs to call your backend, local development usually requires a public tunnel such as ngrok.
The callback endpoint remains externally reachable, but callbacks are accepted only when the URL contains a valid HMAC signature generated by the backend.

Example:
```bash
ngrok http 8080
```

Then configure:
```yaml
judge0:
  callback: https://your-ngrok-url/api/callback/judge0
  callback-secret: ${JUDGE0_CALLBACK_SECRET}
```

### Run the application
```bash
mvn spring-boot:run
```

---

## Design Decisions Reflected in the Code

### 1. Domain separation over one giant package
Authentication, contest logic, and submission execution are split into separate domains.  
This reduces coupling and makes future extension cleaner.

### 2. JWT access token + refresh token rotation
The project does not rely on a single forever-valid token.  
Refresh tokens are persisted, hashed, and revocable.

### 3. Database-backed truth + queue-based execution dispatch
Submissions are stored before execution dispatch.  
RabbitMQ is used for delivery, not as the only source of truth.

### 4. Role-aware API design
Administrative actions are separated from team actions through method-level and route-level authorization.

### 5. External execution through Judge0
Instead of embedding compilers and sandboxes directly into the backend, the system delegates code execution to Judge0, reducing infrastructure complexity in this phase.

---

## Current Limitations

Based on the uploaded source, the following areas still look incomplete or early-stage:
- result queue producer/consumer classes are still empty
- some exceptions are still generic `RuntimeException`
- current config uses `ddl-auto: create-drop`, which is not suitable for production
- current source tree does not show migration tooling
- refresh token security is stronger than basic auth systems, but broader audit/session management can still be expanded

---

## Suggested Next Milestones

### Contest Experience
- contest announcements
- richer scoreboard analytics and export/reporting
- explicit contest participation/join workflow

### Judging Improvements
- richer verdict history
- retry and failure recovery logic
- worker observability and metrics

### Security & Production Readiness
- externalized secrets
- production profile
- structured logging
- database migrations
- better exception taxonomy
- deployment pipeline

### Platform Growth
- WebSocket live updates
- team registration workflow
- plagiarism detection integration
- multi-contest history
- organization / university-level administration

---

## Project Positioning

AuraC² is already beyond a basic student CRUD project.

What makes it stronger is not just the number of entities, but the fact that it already includes:
- stateless JWT security
- refresh token lifecycle handling
- role-based API protection
- contest state management
- asynchronous submission dispatch
- real external judge integration
- callback-driven verdict updates

That makes it a solid foundation for a real online judge system rather than a mock academic prototype.

---

## Disclaimer

This README is based on the uploaded source tree provided in this review.  
If your repository also contains additional root files such as:
- `pom.xml`
- Docker Compose
- frontend code
- deployment configs
- migration scripts

then the final README can be refined further to document those parts precisely.


---

## IntelliJ / Project Structure

Open the repository root so you can see:

```text
AuraC2/
├── UI/
├── backend/
└── docs/
```

Then import the backend with Maven:

1. Open the root folder in IntelliJ.
2. Right-click `backend/pom.xml`.
3. Choose **Add as Maven Project**.
4. Use **JDK 21** to match the Maven configuration.

If IntelliJ still shows old module names, delete `.idea` and any `*.iml` files, then reopen the project.
