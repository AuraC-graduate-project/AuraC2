# AuraC2 Clarifications System
## Architecture and Technical Documentation

## 1. System Component Overview
The Clarifications System is implemented inside the `contestServer` domain as an entity-driven feature with clear separation across layers (Entity, DTO, Repository, Service, Controller).

### 1.1 Entity Layer
- Class: `Clarification`
- File: `backend/src/main/java/com/server/contestControl/contestServer/entity/Clarification.java`
- Table: `clarifications`

Core responsibilities:
- Represents a team question and admin reply lifecycle.
- Tracks:
  - question data
  - reply data
  - visibility
  - status
  - timestamps

Relationships:
- `@ManyToOne Contest contest` (required)
- `@ManyToOne Problem problem` (optional, nullable for general questions)
- `@ManyToOne User user` (team owner of clarification)
- `@ManyToOne User repliedByAdmin` (admin who answered)

Lifecycle defaults:
- `@PrePersist setDefaults()` sets:
  - `createdAt = LocalDateTime.now()`
  - `status = PENDING`

---

### 1.2 Enum Layer
Files:
- `ClarificationStatus.java`
- `ClarificationType.java`
- `StandardReply.java`

Enums used:
- `ClarificationStatus`: `PENDING`, `ANSWERED`, `CLOSED`
- `ClarificationType`: `PRIVATE`, `PUBLIC`
- `StandardReply`:
  - `NO_COMMENT`
  - `READ_PROBLEM_STATEMENT_CAREFULLY`
  - `YES`
  - `NO`
  - `ANSWERED`
  - `CUSTOM`

Design value:
- `StandardReply` reduces reply time during live contests and supports ICPC-style quick responses.

---

### 1.3 DTO Layer
Files:
- `dto/clarification/ClarificationRequest.java`
- `dto/clarification/ReplyRequest.java`
- `dto/clarification/ClarificationResponse.java`

DTO responsibilities:
- `ClarificationRequest`: team submit payload (`contestId`, optional `problemId`, `question`)
- `ReplyRequest`: admin reply payload (`standardReply`, `reply`, `replyType`)
- `ClarificationResponse`: unified read model returned to team/admin/public clients

Mapping strategy:
- `ClarificationResponse.fromEntity(Clarification c)` is used for safe entity-to-DTO transformation.

---

### 1.4 Repository Layer
- Interface: `ClarificationRepository`
- File: `backend/src/main/java/com/server/contestControl/contestServer/repository/ClarificationRepository.java`
- Extends: `JpaRepository<Clarification, Long>`

Important query methods:
- `findByUserIdAndContestIdOrderByCreatedAtDesc(...)`
- `findByContestIdAndReplyTypeAndStatusOrderByCreatedAtDesc(...)`
- `findByContestIdOrderByCreatedAtDesc(...)`
- `findAllByOrderByCreatedAtDesc()`
- `findByStatusOrderByCreatedAtDesc(...)`

Security-critical query:
- Public clarification views are constrained to `PUBLIC + ANSWERED`, preventing pending/private leakage.

---

### 1.5 Service Layer
- Class: `ClarificationService`
- File: `backend/src/main/java/com/server/contestControl/contestServer/service/ClarificationService.java`

Main methods:

1. `submitClarification(...)`
- Validates contest exists.
- Enforces contest status `RUNNING`.
- If `problemId != null`, validates problem exists and belongs to same contest.
- Saves clarification with default `PENDING`.

2. `replyClarification(...)`
- Finds clarification by id.
- Prevents updates if status is `CLOSED`.
- Supports:
  - standard reply flow
  - custom reply flow
  - update existing reply for already answered clarification
- Sets:
  - `replyType`
  - `repliedByAdmin`
  - `repliedAt` (first reply timestamp)
  - `status = ANSWERED`

3. `fetchClarificationsForTeam(...)`
- Validates contest existence and `RUNNING` state.
- Loads:
  - team-owned clarifications
  - public answered clarifications in same contest
- Merges using `Set<Long>` deduplication (`O(n)` expected).
- Sorts newest first.

4. `fetchClarificationsForAdmin(...)`
- Contest-scoped admin list (unfiltered by ownership/visibility).

5. `fetchAllClarificationsForAdmin(...)`
- Global admin list across all contests.

6. `fetchPublicClarifications(...)`
- Public view restricted to `PUBLIC + ANSWERED`.

---

### 1.6 Controller Layer (REST API)
- Class: `ClarificationController`
- File: `backend/src/main/java/com/server/contestControl/contestServer/controller/ClarificationController.java`

| Method | Endpoint | Role | Purpose |
|---|---|---|---|
| `POST` | `/api/clarifications` | `TEAM` | Submit clarification |
| `GET` | `/api/clarifications/my/{contestId}` | `TEAM` | Team view (own + public answered) |
| `GET` | `/api/clarifications/public/{contestId}` | `permitAll` | Public contest clarifications |
| `GET` | `/api/clarifications/admin/contest/{contestId}` | `ADMIN` | Admin contest-scoped view |
| `GET` | `/api/clarifications/admin/all` | `ADMIN` | Admin global view |
| `PUT` | `/api/clarifications/admin/{id}/reply` | `ADMIN` | Reply or update reply |

Controller helper:
- `getCurrentUser()` resolves authenticated user from Spring SecurityContext (not from client payload).

---

## 2. Data and Execution Flow

### Phase 1: Team Submits Clarification
1. Team sends `POST /api/clarifications` with JWT.
2. Security filter validates token and role.
3. Controller resolves authenticated `User`.
4. Service validates contest state and problem ownership.
5. Repository persists clarification.
6. API returns `ClarificationResponse`.

### Phase 2: Admin Reviews Clarifications
1. Admin calls `GET /api/clarifications/admin/contest/{contestId}`.
2. Security enforces `ADMIN`.
3. Service loads contest clarifications ordered by created date.

### Phase 3: Admin Replies
1. Admin calls `PUT /api/clarifications/admin/{id}/reply`.
2. Service resolves clarification and validates status.
3. Service applies standard/custom reply logic and visibility.
4. Status is set to `ANSWERED`.
5. Updated DTO is returned.

### Phase 4: Team Views Responses
1. Team calls `GET /api/clarifications/my/{contestId}`.
2. Service fetches own clarifications + public answered clarifications.
3. Service deduplicates and sorts.
4. Team receives unified timeline.

---

## 3. Security and Cross-Domain Integration

### 3.1 Cross-Domain User Integration
Although clarification logic is in `contestServer`, it references `User` from `authServer`.

Why this is correct:
- Identity ownership remains in `authServer`.
- `contestServer` consumes identity as a foreign-key relationship only.

### 3.2 SecurityConfiguration Integration
File: `backend/src/main/java/com/server/contestControl/authServer/config/SecurityConfiguration.java`

Clarification route policies:
- `/api/clarifications/public/**` -> `permitAll()`
- `/api/clarifications/my/**` -> `hasRole('TEAM')`
- `/api/clarifications/admin/**` -> `hasRole('ADMIN')`
- `/api/clarifications` -> `hasRole('TEAM')`

Pattern order is specific-to-generic to avoid accidental broad matching.

### 3.3 Error Contract
Files:
- `authServer/exception/GlobalExceptionHandler.java`
- `authServer/exception/ErrorResponse.java`

Behavior:
- Domain exceptions return structured JSON (`error`, `message`, `status`, `timestamp`, `path`).
- Validation and unexpected errors are centralized.

---

## 4. Implementation Status

### 4.1 Completed and Working
- End-to-end clarification CRUD flow (submit + fetch + reply/update-reply).
- Team/private/public visibility model.
- Contest-state guarding for team submit/fetch.
- Standard and custom reply support.
- Admin global and contest-specific management views.
- Security integration with role enforcement.

### 4.2 Current Gaps / Missing Items
1. No dedicated endpoint to transition clarification to `CLOSED` (status exists but no close API yet).
2. Clarification request DTOs currently do not use bean validation annotations (`@NotBlank`, `@NotNull`) and controller methods do not use `@Valid`.
3. `replyType` null-guard is not explicitly enforced in service for admin reply payload.
4. Team fetch currently requires contest to be `RUNNING`; after contest end, team cannot fetch via `/my/{contestId}` unless this rule is relaxed.

---

## 5. Test Readiness (Postman Flow)

Recommended sequence:
1. `POST /auth/register` (create admin/team users)
2. `POST /auth/login` as admin
3. `POST /api/contest`
4. `PUT /api/contest/{id}/start`
5. `POST /api/problems`
6. `POST /auth/login` as team
7. `POST /api/clarifications` (team submits)
8. `GET /api/clarifications/admin/contest/{id}` (admin views pending)
9. `PUT /api/clarifications/admin/{id}/reply` (admin replies public/private)
10. `GET /api/clarifications/public/{contestId}` (no token public check)
11. `GET /api/clarifications/my/{contestId}` (team merged view)

Prerequisites:
- PostgreSQL running
- App starts successfully
- JWT auth working

---

## 6. Suggested Next Iteration
1. Add `PATCH /api/clarifications/admin/{id}/close` to activate full status lifecycle.
2. Add bean validation to `ClarificationRequest` and `ReplyRequest` + `@Valid` in controller.
3. Add explicit null checks for `replyType`.
4. Consider allowing team read access after contest ends.
5. Add pagination/filtering for admin endpoints for large contests.

---

## 7. Executive Summary
- The Clarifications System is fully integrated into AuraC2 backend architecture and security model.
- It supports production-style contest workflows (team ask, admin answer, public/private publishing).
- Core domain logic and data isolation are correctly handled.
- A small set of hardening tasks remains to complete lifecycle and payload validation coverage.
