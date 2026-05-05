# AuraC2 Endpoints Update
## Architecture and Technical Documentation

## 1. System Component Overview
The Endpoints Update task was implemented as a focused extension inside the `contestServer` domain, with changes spanning Controller, DTO, Service, and Exception layers. It follows layered architecture and preserves separation of concerns.

Scope of delivered endpoint updates:
- `PUT /api/contest/{id}`
- `PUT /api/problems/{id}`
- `PUT /api/testcases/{id}`

---

### 1.1 Controller Layer
Files:
- `backend/src/main/java/com/server/contestControl/contestServer/controller/ContestController.java`
- `backend/src/main/java/com/server/contestControl/contestServer/controller/ProblemController.java`
- `backend/src/main/java/com/server/contestControl/contestServer/controller/TestCaseController.java`

Responsibilities:
- Define REST contracts (paths, methods, request bindings).
- Enforce access control through `@PreAuthorize`.
- Trigger Bean Validation through `@Valid`.
- Delegate business logic to service layer.

Role restrictions:
- All update endpoints are `ADMIN` only.

---

### 1.2 DTO Layer
New/updated request DTOs:
- `ContestUpdateRequest`
- `ProblemUpdateRequest`
- `TestCaseUpdateRequest`

Validation rules include:
- `@NotBlank` for required text fields.
- `@NotNull` for required structured fields.
- `@Positive` for numeric limits/duration.
- `@Size(max = 255)` for bounded title fields.

Response DTOs:
- `ContestResponse`
- `ProblemResponse`
- `TestCaseResponse`

Design value:
- API contracts are explicit and strongly typed.
- Validation failures are caught at boundary level before business logic execution.

---

### 1.3 Service Layer
Files:
- `ContestService`
- `ProblemService`
- `TestCaseService`

Delivered business logic:

1. Contest update (`updateContestDetails`)
- Loads contest by ID.
- Enforces update is allowed only while contest status is `UPCOMING`.
- Persists and returns updated response.

2. Problem update (`updateProblem`)
- Loads problem by ID.
- Updates title/description/timeLimit/memoryLimit/difficulty.
- Uses centralized `parseDifficulty(...)` for enum-safe mapping.

3. Test case update (`updateTestCase`)
- Loads test case by ID.
- Updates input/output/visibility.
- Persists and returns updated response.

---

### 1.4 Exception Layer
Added exceptions:
- `InvalidDifficultyException`
- `TestCaseNotFoundException`

Used exceptions in update flows:
- `ContestNotFoundException`
- `ProblemNotFoundException`
- `InvalidContestStateException`

Error strategy:
- All domain-specific failures extend `ApiException`.
- Centralized translation to API JSON error response is handled by `GlobalExceptionHandler`.

---

### 1.5 Security Layer Integration
File:
- `backend/src/main/java/com/server/contestControl/authServer/config/SecurityConfiguration.java`

Relevant route policies:
- `/api/contest/**` -> `hasRole("ADMIN")`
- Problem and test case update endpoints are additionally protected at method level via `@PreAuthorize("hasRole('ADMIN')")`.

Result:
- Update operations are strictly admin-only and aligned with contest governance.

---

## 2. Endpoint Contract Matrix

| Method | Endpoint | Role | Request DTO | Response DTO | Purpose |
|---|---|---|---|---|---|
| `PUT` | `/api/contest/{id}` | `ADMIN` | `ContestUpdateRequest` | `ContestResponse` | Update contest title/start/duration |
| `PUT` | `/api/problems/{id}` | `ADMIN` | `ProblemUpdateRequest` | `ProblemResponse` | Update problem metadata and limits |
| `PUT` | `/api/testcases/{id}` | `ADMIN` | `TestCaseUpdateRequest` | `TestCaseResponse` | Update testcase data and visibility |

---

## 3. Request and Response Models

### 3.1 Contest Update Example
Request:
```json
{
  "title": "ACM Warmup 2026",
  "startTime": "2026-04-24T14:00:00Z",
  "durationMinutes": 300
}
```

Response (example):
```json
{
  "id": 7,
  "title": "ACM Warmup 2026",
  "description": "Warmup contest",
  "durationMinutes": 300,
  "status": "UPCOMING",
  "startTime": "2026-04-24T14:00:00Z"
}
```

---

### 3.2 Problem Update Example
Request:
```json
{
  "title": "Two Sum",
  "description": "Find two numbers that sum to target",
  "timeLimit": 1000,
  "memoryLimit": 256,
  "difficulty": "EASY"
}
```

Response (example):
```json
{
  "id": 21,
  "title": "Two Sum",
  "description": "Find two numbers that sum to target",
  "timeLimit": 1000,
  "memoryLimit": 256,
  "difficulty": "EASY",
  "contestId": 7
}
```

---

### 3.3 Test Case Update Example
Request:
```json
{
  "inputData": "5\n1 2 3 4 5\n",
  "expectedOutput": "15\n",
  "isPublic": false
}
```

Response (example):
```json
{
  "id": 98,
  "inputData": "5\n1 2 3 4 5\n",
  "expectedOutput": "15\n",
  "isPublic": false
}
```

---

## 4. Data and Execution Flow

### 4.1 Contest Details Update Flow
1. Admin sends `PUT /api/contest/{id}`.
2. Security enforces admin role.
3. Controller validates payload using `@Valid`.
4. Service loads contest or throws `ContestNotFoundException`.
5. Service verifies status is `UPCOMING`, otherwise throws `InvalidContestStateException`.
6. Service updates fields, saves entity.
7. Updated `ContestResponse` is returned.

---

### 4.2 Problem Update Flow
1. Admin sends `PUT /api/problems/{id}`.
2. Security + controller validation execute first.
3. Service loads problem or throws `ProblemNotFoundException`.
4. Service parses difficulty via `parseDifficulty(...)`.
5. If difficulty invalid, throws `InvalidDifficultyException`.
6. Service updates and saves entity.
7. Updated `ProblemResponse` is returned.

---

### 4.3 Test Case Update Flow
1. Admin sends `PUT /api/testcases/{id}`.
2. Security + controller validation execute first.
3. Service loads testcase or throws `TestCaseNotFoundException`.
4. Service updates input/output/visibility and saves.
5. Updated `TestCaseResponse` is returned.

---

## 5. Validation and Error Handling

Validation is applied at request boundary via:
- `@Valid` on controller methods.
- Constraint annotations inside DTOs.

Error mapping is centralized in:
- `authServer/exception/GlobalExceptionHandler.java`

Error response model:
- `authServer/exception/ErrorResponse.java`

Typical standardized error JSON:
```json
{
  "error": "InvalidContestStateException",
  "message": "Contest name, start time, and duration can only be updated while contest is UPCOMING.",
  "status": 400,
  "timestamp": "2026-04-24T12:10:01Z",
  "path": "/api/contest/7"
}
```

---

## 6. Integration Notes

### 6.1 Domain Placement
- Business logic for these endpoints is fully contained in `contestServer`.
- Security and global error response are reused from `authServer` shared infrastructure.

### 6.2 Frontend Compatibility
- Endpoints return immediate updated DTOs after persistence, enabling direct UI refresh without extra fetch calls.

---

## 7. Test Readiness (Postman Workflow)

Prerequisites:
- PostgreSQL running
- Application started
- Admin account available

Recommended test sequence:
1. `POST /auth/login` as admin
2. `POST /api/contest` create contest
3. `PUT /api/contest/{id}` update contest while `UPCOMING` (expect success)
4. `PUT /api/contest/{id}/start` start contest
5. `PUT /api/contest/{id}` update again (expect `400 InvalidContestStateException`)
6. `POST /api/problems` create problem
7. `PUT /api/problems/{id}` update problem (valid payload)
8. `PUT /api/problems/{id}` with invalid difficulty (expect `400 InvalidDifficultyException`)
9. `POST /api/testcases/{problemId}` create testcase
10. `PUT /api/testcases/{id}` update testcase (valid payload)
11. `PUT /api/testcases/{id}` with missing input/output (expect validation error)

---

## 8. Known Gaps and Improvement Opportunities

1. `ProblemService.getAllProblems(...)` currently returns generic `RuntimeException` when list is empty; can be upgraded to typed API exception for consistency.
2. Problem/TestCase update flows currently do not enforce contest status guard (for example, blocking edits after contest starts); this may be added if policy requires strict freeze.
3. No optimistic locking/versioning is used for concurrent admin edits.
4. Audit trail for who edited contest/problem/testcase is not currently persisted.

---

## 9. Files Added and Modified (Task Scope)

Added:
- `backend/src/main/java/com/server/contestControl/contestServer/dto/contest/ContestUpdateRequest.java`
- `backend/src/main/java/com/server/contestControl/contestServer/dto/problem/ProblemUpdateRequest.java`
- `backend/src/main/java/com/server/contestControl/contestServer/dto/testcase/TestCaseUpdateRequest.java`
- `backend/src/main/java/com/server/contestControl/contestServer/exceptions/InvalidDifficultyException.java`
- `backend/src/main/java/com/server/contestControl/contestServer/exceptions/TestCaseNotFoundException.java`

Modified:
- `backend/src/main/java/com/server/contestControl/contestServer/controller/ContestController.java`
- `backend/src/main/java/com/server/contestControl/contestServer/controller/ProblemController.java`
- `backend/src/main/java/com/server/contestControl/contestServer/controller/TestCaseController.java`
- `backend/src/main/java/com/server/contestControl/contestServer/service/ContestService.java`
- `backend/src/main/java/com/server/contestControl/contestServer/service/ProblemService.java`
- `backend/src/main/java/com/server/contestControl/contestServer/service/TestCaseService.java`
- `backend/src/main/java/com/server/contestControl/contestServer/dto/contest/ContestRequest.java`
- `backend/src/main/java/com/server/contestControl/contestServer/dto/problem/ProblemRequest.java`
- `backend/src/main/java/com/server/contestControl/contestServer/dto/testcase/TestCaseRequest.java`

---

## 10. Executive Summary
- The update endpoints for Contest, Problem, and TestCase are fully implemented and secured.
- Validation and typed exception coverage were upgraded for clearer API behavior.
- Update responses are immediate and frontend-ready.
- Remaining items are primarily policy hardening (state guards, audit, concurrency).
