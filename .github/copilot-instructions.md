# AuraC² - Copilot Instructions

## Project Overview

AuraC² is an online programming contest control system with a **Spring Boot backend** and **React + Vite frontend**. It uses RabbitMQ for asynchronous submission processing and Judge0 for remote code execution.

## Build and Run Commands

### Backend (Spring Boot)

```bash
cd backend/aura-contest-control/jwtAuthServer

# Build
./mvnw clean compile

# Run
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=JwtAuthServerApplicationTests

# Run a single test method
./mvnw test -Dtest=JwtAuthServerApplicationTests#contextLoads
```

### Frontend (React + Vite)

```bash
cd UI

# Install dependencies
npm install

# Development server
npm run dev

# Production build
npm run build
```

### Required Infrastructure

- **PostgreSQL** on `localhost:5432` (database: `authserver`)
- **RabbitMQ** on `localhost:5672` (use Docker: `docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management`)
- **Judge0** callback requires public URL (use ngrok for local development)

## Architecture

### Three-Server Domain Architecture

The backend is organized into three domain-oriented packages under `com.server.contestControl`:

```
authServer/       → Authentication, JWT, refresh tokens, user management
contestServer/    → Contests, problems, test cases, admin operations
submissionServer/ → Code submissions, RabbitMQ queue, Judge0 integration
```

Each server has its own: `controller/`, `service/`, `repository/`, `dto/`, `entity/`, `enums/`

### Submission Pipeline (Asynchronous)

1. User submits code → saved to PostgreSQL with `PENDING` verdict
2. Submission ID published to RabbitMQ (`SubmissionProducer`)
3. `SubmissionConsumer` loads submission, sends each test case to Judge0
4. Judge0 calls back `/api/callback/judge0/{submissionId}/{testCaseNumber}`
5. `CallbackHandler` updates verdict on first failure or final success

### JWT Security Model

- **Access token**: short-lived, sent in Authorization header
- **Refresh token**: long-lived, stored in HTTP-only cookie, hashed in database
- Refresh tokens support rotation and revocation
- Roles: `ADMIN`, `TEAM`

### Frontend Architecture

- Role-based routing: `AdminApp` or `TeamApp` based on JWT role
- Token stored in memory/localStorage, auto-refresh on app boot
- Uses shadcn/ui components (Radix UI primitives + Tailwind)

## Code Conventions

### DTOs

- Use Java records for request/response DTOs
- Include static `fromEntity()` factory method in response DTOs:
  ```java
  public record ContestResponse(...) {
      public static ContestResponse fromEntity(Contest contest) { ... }
  }
  ```

### Service Layer Patterns

- Use `@RequiredArgsConstructor` with `final` fields for constructor injection
- Facade pattern for complex flows (see `AuthFacade`)
- Services retrieve entities, controllers only handle HTTP concerns

### Exception Handling

- Custom exceptions extend `ApiException` base class in `authServer/exception/api/`
- Global handler in `GlobalExceptionHandler` returns standardized `ErrorResponse`

### Entities

- Use Lombok `@Builder`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
- JPA entities use `@Entity` with explicit `@Table` names

### Naming

- Package structure: `{domain}Server/{layer}` (e.g., `contestServer/service`)
- Enums in singular form: `Verdict`, `ContestStatus`, `Role`
- Repository methods follow Spring Data JPA naming conventions

## Configuration

Application config is in `backend/.../src/main/resources/application.yml`. Key properties:

- `judge0.url` and `judge0.callback`: Judge0 integration endpoints
- `jwt.access-secret` / `jwt.refresh-secret`: Token signing keys
- `spring.jpa.hibernate.ddl-auto`: Currently `create-drop` (not production-ready)

**Note**: Current config has hardcoded secrets—use environment variables for production.

## API Route Security

| Route Pattern | Access |
|--------------|--------|
| `/auth/**` | Public |
| `/api/callback/judge0/**` | Public (Judge0 callbacks) |
| `/api/contest/active,upcoming,paused,ended` | Public (read-only) |
| `/api/contest/**` (mutations) | ADMIN only |
| `/api/submissions/**` | TEAM or ADMIN |
| `/api/admin/**` | ADMIN only |
