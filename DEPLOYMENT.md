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

Edit `backend/aura-contest-control/jwtAuthServer/src/main/resources/application.yml`:

```yaml
judge0:
  callback: "https://YOUR-NGROK-URL/api/callback/judge0"  # Replace with ngrok URL
```

### 4. Start Backend

```bash
cd backend/aura-contest-control/jwtAuthServer
./mvnw spring-boot:run
```

### 5. Start Frontend

```bash
cd UI
npm install  # First time only
npm run dev
```

---

## Option 2: Full Docker Deployment

Run everything in Docker containers (requires building images).

### 1. Create .env file

```bash
cp .env.example .env
```

Edit `.env` and set your ngrok URL:
```
JUDGE0_CALLBACK_URL=https://your-ngrok-url.ngrok-free.app/api/callback/judge0
```

### 2. Uncomment backend and frontend services

Edit `docker-compose.yml` and uncomment the `backend` and `frontend` services.

### 3. Build and Start All Services

```bash
docker-compose up --build
```

---

## 📋 Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost:5173 (dev) or http://localhost:80 (docker) | - |
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
docker-compose logs -f postgres
docker-compose logs -f rabbitmq
```

### Stop Services
```bash
# Stop all services
docker-compose down

# Stop and remove volumes (deletes database data)
docker-compose down -v
```

### Restart Services
```bash
# Restart all
docker-compose restart

# Restart specific service
docker-compose restart postgres
```

### Check Service Health
```bash
docker-compose ps
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
Edit `application.yml`:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # Change from create-drop
```

### 2. Use Environment Variables for Secrets
Instead of hardcoded values, use:
```yaml
spring:
  datasource:
    password: ${POSTGRES_PASSWORD}
jwt:
  access-secret: ${JWT_ACCESS_SECRET}
  refresh-secret: ${JWT_REFRESH_SECRET}
```

### 3. Self-host Judge0
The current setup uses Judge0 CE (public). For production:
- Deploy your own Judge0 instance
- Update `judge0.url` in configuration

### 4. Use a Proper Tunneling Solution
ngrok free tier resets URLs on restart. Consider:
- Setting up a proper reverse proxy
- Using ngrok paid tier with fixed domains
- Deploying to a cloud provider with public IPs

### 5. Enable HTTPS
Configure SSL certificates for production deployment.

---

## 🆘 Troubleshooting

### PostgreSQL won't start
```bash
# Check if port 5432 is in use
lsof -i :5432

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

### Backend can't connect to services
- Ensure Docker services are running: `docker-compose ps`
- Check connection strings in `application.yml` use `localhost`
- For full Docker deployment, use service names (`postgres`, `rabbitmq`)

### ngrok URL expired
- Restart ngrok: `ngrok http 8080`
- Update `application.yml` with new URL
- Restart backend

---

## 📦 Clean Installation

To completely reset everything:

```bash
# Stop all containers and remove volumes
docker-compose down -v

# Remove all AuraC² Docker images
docker images | grep aura | awk '{print $3}' | xargs docker rmi -f

# Start fresh
docker-compose up -d
```
