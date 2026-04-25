# 🚀 AuraC² Deployment Guide

## Quick Start with Docker Compose

### Prerequisites
- Docker Desktop installed ([Download](https://www.docker.com/products/docker-desktop))
- ngrok installed for Judge0 callbacks ([Download](https://ngrok.com/download))

---

## Option 1: Infrastructure Only (Recommended for Development)

Run PostgreSQL and RabbitMQ in Docker, backend and frontend locally.

### 1. Start Infrastructure

```bash
# Start PostgreSQL and RabbitMQ
docker-compose up -d

# Check services are running
docker-compose ps
```

### 2. Start ngrok (in a separate terminal)

```bash
ngrok http 8080
```

Copy the HTTPS URL (e.g., `https://xxxx.ngrok-free.app`)

### 3. Update Backend Configuration

Edit `backend/src/main/resources/application.yml`:

```yaml
judge0:
  callback: "https://YOUR-NGROK-URL/api/callback/judge0"  # Replace with ngrok URL
```

### 4. Start Backend

```bash
cd backend
./mvnw spring-boot:run
```

### 5. Start Frontend

```bash
cd UI
npm install  # First time only
npm run dev
```

---

## Option 2: Full Docker Deployment (Recommended for Production)

Run everything in Docker containers (backend, frontend, PostgreSQL, RabbitMQ).

### 1. Create .env file

```bash
cp .env.example .env
```

Edit `.env` and set your values:
```bash
# Judge0 callback (use ngrok URL for development or production URL)
JUDGE0_CALLBACK_URL=https://your-ngrok-url.ngrok-free.app/api/callback/judge0

# JWT Secrets (IMPORTANT: Change these in production!)
JWT_ACCESS_SECRET=your-super-secret-access-key-min-32-chars
JWT_REFRESH_SECRET=your-super-secret-refresh-key-min-32-chars
```

### 2. Build and Start All Services

```bash
docker-compose up --build
```

Wait for all services to be healthy (check with `docker-compose ps`).

### 3. Access the Application

- **Frontend**: http://localhost
- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html

---

## 📋 Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost | - |
| Backend API | http://localhost:8080 | - |
| PostgreSQL | localhost:5432 | postgres/1234 |
| RabbitMQ AMQP | localhost:5672 | guest/guest |
| RabbitMQ Management | http://localhost:15672 | guest/guest |
| Swagger API Docs | http://localhost:8080/swagger-ui.html | - |
| ngrok Inspector | http://127.0.0.1:4040 | - |

---

## 🔧 Common Commands

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f postgres
docker-compose logs -f rabbitmq
```

### Stop Services
```bash
# Stop all services
docker-compose down

# Stop and remove volumes (deletes database data!)
docker-compose down -v
```

### Restart Services
```bash
# Restart all
docker-compose restart

# Restart specific service
docker-compose restart backend
```

### Check Service Health
```bash
docker-compose ps
```

### Rebuild Services
```bash
# Rebuild and restart (after code changes)
docker-compose up --build -d
```

---

## 🗄️ Database Management

### Connect to PostgreSQL
```bash
docker-compose exec postgres psql -U postgres -d authserver
```

### Backup Database
```bash
docker-compose exec postgres pg_dump -U postgres authserver > backup.sql
```

### Restore Database
```bash
docker-compose exec -T postgres psql -U postgres authserver < backup.sql
```

---

## 🐰 RabbitMQ Management

Access the RabbitMQ Management UI at http://localhost:15672

Default credentials: `guest` / `guest`

You can monitor:
- Queues and messages
- Connections and channels
- Exchange bindings

---

## 🔄 Production Considerations

### 1. Change Database Persistence
Edit `application.yml` or set environment variable:
```bash
SPRING_JPA_DDL_AUTO=update
```

**Important**: Change from `create-drop` to `update` for production to preserve data.

### 2. Use Strong Secrets
In your `.env` file (never commit this!):
```bash
JWT_ACCESS_SECRET=generate-a-strong-random-string-at-least-32-chars
JWT_REFRESH_SECRET=generate-another-strong-random-string
POSTGRES_PASSWORD=use-a-strong-database-password
```

Generate strong secrets:
```bash
# Linux/Mac
openssl rand -base64 32

# Or use this online tool: https://generate-secret.vercel.app/32
```

### 3. Self-host Judge0
The current setup uses Judge0 CE (public). For production:
- Deploy your own Judge0 instance
- Update `JUDGE0_URL` in `.env`

### 4. Configure Proper Callback URL
For production, use your actual domain:
```bash
JUDGE0_CALLBACK_URL=https://your-domain.com/api/callback/judge0
```

### 5. Enable HTTPS
Configure SSL certificates or use a reverse proxy (nginx, traefik) with Let's Encrypt.

### 6. Resource Limits
Add resource limits to `docker-compose.yml` for production:
```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 1G
```

---

## 🆘 Troubleshooting

### Backend won't start
```bash
# Check logs
docker-compose logs backend

# Common issues:
# - Database not ready: wait for postgres healthcheck
# - RabbitMQ not ready: wait for rabbitmq healthcheck
# - Port already in use: change port in docker-compose.yml
```

### PostgreSQL won't start
```bash
# Check if port 5432 is in use
netstat -ano | findstr :5432

# Remove existing container and volume
docker-compose down -v
docker-compose up -d postgres
```

### RabbitMQ connection refused
```bash
# Wait for RabbitMQ to fully start (can take 30-60 seconds)
docker-compose logs rabbitmq

# Check health
docker-compose exec rabbitmq rabbitmq-diagnostics ping
```

### Frontend can't connect to backend
- Ensure backend is running: `docker-compose ps backend`
- Check nginx config in `UI/nginx.conf`
- Verify API_BASE_URL environment variable if set

### Judge0 callback fails
- Ensure ngrok is running: `ngrok http 8080`
- Update `JUDGE0_CALLBACK_URL` in `.env` with current ngrok URL
- Rebuild backend: `docker-compose up --build backend`

### Database schema keeps resetting
- Change `SPRING_JPA_DDL_AUTO` to `update` in `.env`
- Restart: `docker-compose down && docker-compose up -d`

---

## 📦 Clean Installation

To completely reset everything:

```bash
# Stop all containers and remove volumes
docker-compose down -v

# Remove all AuraC² Docker images
docker images | grep aura | awk '{print $3}' | xargs docker rmi -f

# Start fresh
docker-compose up --build
```

---

## 🏗️ Architecture Overview

```
┌─────────────┐     ┌─────────────┐
│  Frontend   │────▶│   Backend   │
│   (Nginx)   │     │ (Spring Boot)│
│   Port 80   │     │   Port 8080  │
└─────────────┘     └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │PostgreSQL│ │ RabbitMQ │ │  Judge0  │
        │  :5432   │ │  :5672   │ │(External)│
        └──────────┘ └──────────┘ └──────────┘
```

---

## 📝 Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/authserver` | Database connection URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `1234` | Database password |
| `SPRING_JPA_DDL_AUTO` | `create-drop` | Hibernate DDL mode |
| `SPRING_RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `SPRING_RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `SPRING_RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `SPRING_RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `JUDGE0_URL` | `https://ce.judge0.com/submissions?wait=false` | Judge0 API URL |
| `JUDGE0_CALLBACK_URL` | (ngrok URL) | Callback URL for Judge0 |
| `JWT_ACCESS_SECRET` | (default key) | JWT access token secret |
| `JWT_REFRESH_SECRET` | (default key) | JWT refresh token secret |
