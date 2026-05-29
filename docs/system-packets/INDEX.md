# System Analysis Packet Index

| Order | Packet | Covers | Why read it here |
| ----- | ------ | ------ | ---------------- |
| 1 | [00-system-overview-packet.md](00-system-overview-packet.md) | Project shape, backend/frontend modules, infrastructure, feature classification | Establishes the map before subsystem details |
| 2 | [01-auth-session-packet.md](01-auth-session-packet.md) | Login, JWT, refresh cookies, logout, bootstrap admin, no-security guard | Security context explains all later authorization claims |
| 3 | [02-user-team-admin-management-packet.md](02-user-team-admin-management-packet.md) | Admin user APIs, team account generation, credential export | Defines team/admin identities used by contests and scoring |
| 4 | [03-contest-lifecycle-scheduler-packet.md](03-contest-lifecycle-scheduler-packet.md) | Contest state, effective state, schedulers, row locking | Contest timing gates submissions, clarifications, SSE, and scoreboard |
| 5 | [04-sse-live-events-packet.md](04-sse-live-events-packet.md) | SSE registries, streams, heartbeat, frontend hooks | Live update transport is reused by contests, submissions, clarifications, and scoreboard |
| 6 | [05-problems-testcases-statements-packet.md](05-problems-testcases-statements-packet.md) | Problems, statements, test cases, validators metadata, PDFs | Defines what teams solve and what judging consumes |
| 7 | [06-submission-judging-rabbitmq-judge0-packet.md](06-submission-judging-rabbitmq-judge0-packet.md) | Official submit flow, RabbitMQ, Judge0 callbacks, comparison, validators | Core scoring pipeline depends on prior contest/problem context |
| 8 | [07-team-run-custom-tests-packet.md](07-team-run-custom-tests-packet.md) | Non-scoring Run, custom tests, public samples, synchronous Judge0 | Separates practice execution from official submissions |
| 9 | [08-rejudge-packet.md](08-rejudge-packet.md) | Selected/problem/contest rejudge, force rejudge, judgeRunId | Explains how existing submissions are superseded |
| 10 | [09-scoreboard-freeze-reveal-packet.md](09-scoreboard-freeze-reveal-packet.md) | Ranking, freeze, reveal, public/admin snapshots, filtering | Builds on finalized submissions and contest timing |
| 11 | [10-clarifications-packet.md](10-clarifications-packet.md) | Team questions, admin replies, public/private visibility, SSE | Contest communication subsystem |
| 12 | [11-oracle-generated-tests-validators-packet.md](11-oracle-generated-tests-validators-packet.md) | Reference solutions, generators, validators, generated tests, counterexamples | Admin-side test engineering workflow |
| 13 | [12-contest-team-moderation-audit-packet.md](12-contest-team-moderation-audit-packet.md) | Hide/disqualify/disable submit/run, audit logs, CSV, blocked team page | Cross-cutting policy that affects workspace, submission, run, scoreboard |
| 14 | [13-data-model-flyway-er-packet.md](13-data-model-flyway-er-packet.md) | JPA entities, migrations, constraints, ER skeleton | Consolidated schema evidence after behavior packets |
| 15 | [14-frontend-admin-team-routing-packet.md](14-frontend-admin-team-routing-packet.md) | React role routing, admin/team apps, API clients, hooks | Shows how UI reaches the backend subsystems |
| 16 | [15-known-risks-gaps-packet.md](15-known-risks-gaps-packet.md) | Consolidated security, consistency, async, correctness, testing risks | Final cross-system gap register |
