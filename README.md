# AuraC2 - Aura Contest Control
## Tech Stack

### Backend
![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.2-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![Spring JPA](https://img.shields.io/badge/Spring%20Data-JPA-6DB33F?style=flat-square&logo=spring&logoColor=white)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white)
![Swagger](https://img.shields.io/badge/API%20Docs-Swagger-85EA2D?style=flat-square&logo=swagger&logoColor=black)
![Lombok](https://img.shields.io/badge/Lombok-1.18-red?style=flat-square&logo=java&logoColor=white)
![Flyway](https://img.shields.io/badge/Migrations-Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white)

### Database & Messaging
![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL%2015-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/Messaging-RabbitMQ-FF6600?style=flat-square&logo=rabbitmq&logoColor=white)

### Frontend
![React](https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5.7-3178C6?style=flat-square&logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Build-Vite-646CFF?style=flat-square&logo=vite&logoColor=white)
![TailwindCSS](https://img.shields.io/badge/Styling-TailwindCSS%20v4-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white)
![Radix UI](https://img.shields.io/badge/UI-Radix%20UI-161618?style=flat-square&logo=radixui&logoColor=white)
![React Hook Form](https://img.shields.io/badge/Forms-React%20Hook%20Form-EC5990?style=flat-square&logo=reacthookform&logoColor=white)
![Recharts](https://img.shields.io/badge/Charts-Recharts-22B5BF?style=flat-square&logo=chartdotjs&logoColor=white)

### Infrastructure & Services
![Docker](https://img.shields.io/badge/Container-Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![Nginx](https://img.shields.io/badge/Proxy-Nginx-009639?style=flat-square&logo=nginx&logoColor=white)
![Judge0](https://img.shields.io/badge/Code%20Execution-Judge0-blueviolet?style=flat-square&logo=codeforces&logoColor=white)
![Gmail](https://img.shields.io/badge/Email-Gmail%20SMTP-EA4335?style=flat-square&logo=gmail&logoColor=white)


AuraC2 is a university programming contest-control system. It combines a Spring Boot backend, a React/Vite frontend, PostgreSQL persistence, RabbitMQ queueing, and Judge0 execution to support ICPC-style contest administration and team submissions.

The current implementation is a modular monolith: backend packages are organized by domain (`authServer`, `contestServer`, `submissionServer`, and shared utilities), but they run in one Spring Boot application and share one database.

## Current Source Of Truth

Use these active files for current implementation documentation:

- `README.md`
- `UI/README.md`
- `docs/report-rebuild/analysis-plan.md`
- `docs/report-rebuild/report-draft-google-docs.md`
- `docs/report-rebuild/diagram-prompts.md`

Historical reports, old feature writeups, task prompts, and duplicate PDFs are archived under `docs/archive/`. Archived files are historical source material only and should not be treated as current implementation truth.

## Technology Stack

| Layer | Current implementation |
|---|---|
| Backend | Spring Boot 3.4.x, Java 21 |
| Frontend | React 18, TypeScript, Vite, Tailwind |
| Database | PostgreSQL |
| Migrations | Flyway V1-V6 with Hibernate schema validation |
| Queue | RabbitMQ submission queue |
| Judge | Judge0 API and signed callback endpoint |
| Auth | JWT access tokens and HTTP-only refresh-token cookie flow |
| Real-time updates | Server-sent events for contest, submissions, clarifications, and scoreboard |

## Implemented Features

### Authentication And Roles

- Login with JWT access token.
- Refresh-token persistence, rotation, logout revocation, and cookie clearing.
- Administrator-protected team registration.
- `ADMIN` and `TEAM` roles with route-level and method-level authorization.
- Guarded `no-security` profile for local/test use only.

### Contest Management

- Contest create/update and public status buckets for active, upcoming, paused, and ended contests.
- Manual start, pause, resume, and end.
- Jury override for early end.
- Effective state calculation using schedule, actual start time, pause time, and duration.
- Exact-time transition scheduler plus fallback synchronization.
- Contest lifecycle SSE with snapshot, update, heartbeat, and frontend fallback polling.

### Problems And Test Cases

- Admin problem create/read/update/delete.
- Structured problem statements support statement body, input format, output format, constraints, public notes, admin internal notes, time limit, and memory limit.
- Legacy `description` remains readable and is used as the statement fallback for older problems.
- Public notes are contestant-facing and appear in team statement views and contestant-style previews.
- Admin internal notes are admin-only and are excluded from team views and SAFE_MODE prompt exports.
- Admin problem details include contestant-style preview and readiness warnings for missing key statement fields.
- Admin test-case create/read/update/delete.
- Hidden/private test cases remain admin/internal only.
- TEAM users can fetch only public/sample test cases through the public sample endpoint.
- Private inputs and expected outputs are not exposed to TEAM APIs.
- Problem deletion is blocked when submissions, clarifications, scoreboard reveal cells, or generated oracle history depend on the problem.

### Submissions And Judging

- Submission creation validates authenticated user, active running contest, and problem membership.
- New submission messages are published to RabbitMQ only after the database transaction commits.
- RabbitMQ consumer locks/claims queueable submissions to reduce duplicate dispatch.
- Judge0 receives one request per test case.
- Judge0 callback URLs include `submissionId`, `judgeRunId`, `testCaseNumber`, and an HMAC signature.
- Missing or invalid callback signatures are rejected before state changes.
- `judgeRunId` protects rejudge runs from stale callbacks.
- Per-test-case `SubmissionJudgeResult` rows are stored idempotently with a unique `(submission_id, judge_run_id, test_case_number)` constraint.
- Zero-test submissions become `INTERNAL_ERROR` rather than remaining `RUNNING`.
- Final verdict aggregation waits for expected terminal results and uses the earliest non-accepted test case by test-case number.

### Compare Policies

Problem-level compare policies are implemented:

- `EXACT`: default. Keeps the Judge0 `expected_output` path for backward compatibility.
- `NORMALIZED_TEXT`: backend compares decoded Judge0 stdout with normalized text handling.
- `TOKEN_NORMALIZED`: backend compares whitespace-separated tokens.
- `FLOAT_TOLERANCE`: backend compares numeric tokens with absolute and relative epsilon values.

Execution errors bypass output comparison. Hidden expected output is not exposed to TEAM users.

### Custom Output Validators

Problems can use `CUSTOM_VALIDATOR` mode for deterministic special checking:

- Validator source/configuration is admin-only.
- Validator code is executed through Judge0, not inside the backend JVM or host shell.
- The checker receives hidden input, reference output, and team output through a bounded contract.
- Checker crashes, timeouts, invalid output, or Judge0 failures map to a safe internal judging error.
- Interactive problems are not supported.

### Reference Oracle And Generated Tests

AuraC2 includes an admin-only deterministic hybrid judging extension:

1. Test Preparation:
   - Admin configures a reference solution, input generator, and optional input validator.
   - Generator, validator, and reference solution execution go through Judge0.
   - Generated cases are candidates until promoted.
   - Admin can promote one, selected, or all valid generated cases to official hidden `TestCase` records.
   - Promoted hidden tests are used by normal future submissions and rejudge.

2. Counterexample Search:
   - Admin may run generated tests against one existing submission.
   - Mismatches store concrete counterexamples with generated input, reference output, team output, and diagnostics.
   - Admin can promote counterexamples to hidden official test cases.

Generated tests help discover bugs but do not prove correctness. ML is not used as a verdict source.

### Problem Engineering Prompt Exports

Admins can export deterministic prompt text from the Problem Engineering/Oracle panel for optional external use:

- Reference solution prompt.
- Input generator prompt.
- Input validator prompt.
- Checker/output validator prompt.
- Full problem engineering bundle prompt.

Prompt exports do not call OpenAI, Codex, Gemini, Claude, or any external AI provider. They only assemble copyable/exportable text from AuraC2 problem data, public samples, compare policy, selected language contracts, and explicitly selected admin instructions. SAFE_MODE excludes hidden tests, hidden expected outputs, admin internal notes, and official source snippets. ADMIN_FULL_MODE requires explicit confirmation before sensitive material can be included.

Target languages come from the backend supported-language catalog used by Judge0 mapping. The prompt renderer validates the requested language server-side and adapts file names, entry point expectations, runtime notes, and verification instructions to the selected language. C++17 is not assumed unless selected.

### Rejudge

- Admin can rejudge selected submissions, all submissions for a problem, or all submissions for a contest.
- Force rejudge is supported for active or final submissions.
- Rejudge publish happens after commit.
- Old results are superseded by a new `judgeRunId`; stale callbacks are ignored.
- Admin UI includes rejudge workflows.

### Scoreboard And Clarifications

- ICPC-style scoreboard ranking with solved counts, penalties, first-to-solve cells, public/admin snapshots, and SSE.
- Freeze and reveal workflows are implemented with admin reveal controls.
- Clarifications are wired in backend and frontend:
  - TEAM users can submit questions and view own/public answers.
  - ADMIN users can review and reply publicly or privately.
  - Clarification SSE refreshes admin/team views.

## Known Limitations

- Announcements are not implemented.
- Security monitoring is a placeholder UI without backend support.
- Dashboard statistics endpoints are not implemented.
- Result queue classes exist as scaffold, but verdict delivery currently uses submission SSE rather than a result queue.
- There is no explicit contest participation/join workflow; teams are users with role `TEAM`.
- Full LAN/offline deployment requires an explicitly configured local Judge0 instance.
- Interactive problems are not supported.
- ML is never used to judge submissions.
- Prompt exports do not integrate with AI APIs and do not make generated code official.
- Generated tests and counterexamples are deterministic aids, not mathematical proof of correctness.

## Local Development

### Prerequisites

- Java 21
- Maven
- Node.js and npm
- Docker Desktop, if using Docker for PostgreSQL/RabbitMQ
- Judge0 access. For public Judge0 callbacks from local development, use a tunnel such as ngrok and set `JUDGE0_CALLBACK_URL`.

### Start Infrastructure

The checked-in `docker-compose.yml` actively runs PostgreSQL and RabbitMQ. Backend and frontend service definitions are present but commented out.

Create a local `.env` from `.env.example` or set the required `POSTGRES_*` and `RABBITMQ_*` variables before starting Compose.

```bash
docker compose up -d
```

PostgreSQL listens on `localhost:5432`, and RabbitMQ listens on `localhost:5672` with the management UI on `localhost:15672`.

### Run Backend

```bash
mvn -f backend/pom.xml spring-boot:run
```

Important environment variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_RABBITMQ_HOST`
- `JUDGE0_URL`
- `JUDGE0_CALLBACK_URL`
- `JUDGE0_CALLBACK_SECRET`
- `JWT_ACCESS_SECRET`
- `JWT_REFRESH_SECRET`

The default profile is `dev`. Normal startup uses Flyway migrations and `spring.jpa.hibernate.ddl-auto=validate` by default. Do not rely on `create-drop` for normal development.

### Run Frontend

```bash
cd UI
npm install
npm run dev
```

Open the URL printed by Vite.

## Database Migrations

Flyway migrations are under `backend/src/main/resources/db/migration`:

- `V1__baseline_schema.sql`
- `V2__problem_compare_policy.sql`
- `V3__problem_custom_validators.sql`
- `V4__reference_oracle_generated_tests.sql`
- `V5__generated_test_batch_partial_status.sql`
- `V6__structured_problem_statements.sql`

Manual repair scripts, if any, belong outside `db/migration` and are not part of the official forward-only migration history.

To reset a local development database intentionally, stop the application, reset the PostgreSQL volume or schema, then restart so Flyway can apply V1-V6 from a clean state. This is a local reset operation, not the normal startup workflow.

## Verification Commands

```bash
mvn -q -f backend/pom.xml -DskipTests compile
mvn -q -f backend/pom.xml test
cd UI
npm run build
```

## Fair Comparison Note

AuraC2 should be described as a university contest-control system with selected ICPC-style features. Mature systems such as DOMjudge and PC2 remain broader contest infrastructure projects. AuraC2 documentation should not claim superiority over DOMjudge or imply unsupported features such as interactive judging, proof of correctness, or ML-based verdicts.
