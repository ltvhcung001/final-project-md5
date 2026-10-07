# Omnichannel E-Commerce Platform 

---
## 1. Tech Stack

- Language / framework: Java 25, Spring Boot 3.5.x, Maven
- API Gateway: Spring Cloud Gateway **Server MVC** with virtual threads enabled; Nginx sits in front for TLS termination
- Identity: Spring Security, JWT (jjwt or Nimbus), Spring Data JPA, PostgreSQL
- Product: Spring Data MongoDB, Spring Data Elasticsearch, gRPC server (`grpc-spring-boot-starter`)
- Order & Inventory: Spring Data JPA, Redisson (Redis lock), gRPC client, Spring AMQP
- Payment: Spring Data JPA, Spring AMQP, REST clients for VNPay/MoMo (use the sandbox)
- Notification: Spring AMQP, Spring Mail, Redis or MongoDB to store the delivery log
- Message broker: RabbitMQ 
- Monitoring: Actuator + Micrometer, Prometheus, Grafana, Loki + Promtail, (optional) OpenTelemetry + Jaeger

Note on the gateway: the Server MVC variant has fewer built-in features than the WebFlux one. Before committing, verify in the docs of your chosen Spring Cloud version that rate limiting, circuit breaker (Resilience4j) and retry are available. Fallback options: Nginx as the gateway with `auth_request`, or a thin custom Spring MVC gateway.

---

## 2. Codebase Structure (Maven multi-module monorepo)

```
omnichannel/
├── pom.xml                      # parent, manages versions
├── common-lib/                  # ApiResponse {code,message,data}, exceptions, event DTOs
├── proto-contracts/             # .proto files for gRPC Order <-> Product
├── api-gateway/                 # :8080
├── identity-service/            # :8081
├── product-service/             # :8082 (gRPC :9092)
├── order-inventory-service/     # :8083
├── payment-service/             # :8084
├── notification-service/        # :8085
├── deploy/
│   ├── docker-compose.production.yml
│   ├── nginx/
│   └── monitoring/              # prometheus.yml, loki, promtail
└── .github/workflows/deploy.yml
```

Note: a monorepo makes it easy to share `common-lib` and the proto files. If you want to show that teams work independently, split it into multiple repos.

---

## 3. Responsibilities of Each Service

### 3.1. API Gateway
- Routing: `/api/auth/**`, `/api/products/**`, `/api/orders/**`, `/api/payments/**`
- Verify JWT (only needs the secret or public key, no DB call)
- Rate limiting per IP or per user
- Add `X-User-Id` and `X-User-Roles` headers so downstream services can trust them
- Strip any `X-User-*` headers sent by the client

### 3.2. Identity & User Service (PostgreSQL)
- Register, login, refresh token
- Manage shipping addresses
- RBAC (`CUSTOMER`, `ADMIN`)
- Store refresh tokens in the DB or Redis so they can be revoked

### 3.3. Product Catalog Service (MongoDB + Elasticsearch)
- CRUD for products, categories, brands, SKU variants
- Sync data to Elasticsearch for full-text search
- Expose gRPC `GetProduct` and `GetProducts` for the Order Service

### 3.4. Order & Inventory Service (PostgreSQL + Redis) — core service
- Create order: call Product over gRPC for name and price, then store a **snapshot** in the order (Bounded Context)
- State machine: `PENDING → CONFIRMED → SHIPPING → COMPLETED / CANCELLED`
- Tables: `inventory (sku, available, reserved, version)` and `stock_reservation`
- Stock deduction:
  - Redis as a fast guard (atomic Lua `DECR` script or Redisson lock)
  - Postgres as the source of truth: optimistic lock (`@Version`) or `UPDATE ... SET available = available - ? WHERE available >= ?`
- **Idempotency key** on the place-order API so client retries do not create duplicate orders
- **Outbox pattern:** an `outbox_event` table written in the same transaction as the order; a scheduler reads it and publishes to RabbitMQ
- Job to cancel `PENDING` orders after a timeout (for example 15 minutes) and release the stock

### 3.5. Payment & Billing Service (PostgreSQL)
- Create a transaction on `OrderCreatedEvent` and generate the payment URL
- Handle webhooks/IPN: verify signatures, keep handlers idempotent
- Refunds and reconciliation
- Publish `PaymentSucceeded` / `PaymentFailed`

### 3.6. Notification Service (Redis or MongoDB)
- Consume events and send email (SMTP / SendGrid / Mailtrap), SMS / push (can be mocked)
- Store the delivery log
- May go down without affecting order placement; the queue holds pending messages

---

## 4. Saga Flow (choreography)

1. The client calls `POST /orders`.
2. The Order Service reserves stock, saves the order as `PENDING`, and publishes `OrderCreated`.
3. The Payment Service consumes the event, creates a transaction, and publishes `PaymentSucceeded` or `PaymentFailed`.
4. The Order Service handles the result:
   - Success: move to `CONFIRMED` and commit the stock deduction.
   - Failure: move to `CANCELLED` and **release the reserved stock** (compensating transaction).
5. The Notification Service listens to every status change and sends emails.

---

## 5. Infrastructure and Server Configuration

### 5.1. VPS
- Ubuntu 22.04 LTS, at least 4 vCPU, 8GB RAM, 50GB SSD
- Install: Docker, Docker Compose plugin, Nginx, UFW

### 5.2. Firewall

```bash
sudo ufw default deny incoming
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Note: Docker bypasses UFW when a port is published. In compose, **do not use `ports:`** for the DBs, Redis, and RabbitMQ (internal network only). Publish the gateway only as `127.0.0.1:8080:8080`.

### 5.3. Domain and DNS

Point A records to the VPS IP:

| Domain | Purpose |
|---|---|
| `api.<domain>` | Nginx → API Gateway |
| `grafana.<domain>` | Monitoring dashboard (enable login) |
| `<domain>` / `www.<domain>` | Frontend (optional) |

- Do not expose RabbitMQ management or Prometheus publicly; use an SSH tunnel.
- Issue certificates:

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d api.<domain> -d grafana.<domain>
```

### 5.4. Nginx (reverse proxy)
- `proxy_pass http://127.0.0.1:8080;`
- Headers `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Real-IP`
- Redirect HTTP to HTTPS

### 5.5. Docker Compose (`docker-compose.production.yml`)
- One shared network `backend-network`
- The 6 application services
- Infrastructure:
  - Postgres (one instance with 3 separate databases for identity / order / payment, one user per service, no cross-access)
  - MongoDB, Elasticsearch (single node), Redis, RabbitMQ
  - Prometheus, Grafana, Loki, Promtail (Jaeger last)
- For each container: `restart: unless-stopped`, `healthcheck`, persistent volume, memory limit

### 5.6. RAM notes (8GB)
- Each Java service: `-Xmx256m` to `-Xmx384m` (or `-XX:MaxRAMPercentage`)
- Elasticsearch: `ES_JAVA_OPTS=-Xms512m -Xmx512m`
- Enable 2–4GB of swap
- If memory is still tight: drop Jaeger, or add Elasticsearch in a later phase

### 5.7. Sample Java Dockerfile (multi-stage)

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY . .
RUN mvn -q -pl order-inventory-service -am package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/order-inventory-service/target/*.jar app.jar
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
```

### 5.8. Gateway configuration (Server MVC + virtual threads)

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

This is set in every service's `application.yml`. Virtual threads need Java 21 or newer; this project targets Java 25.

- Use `spring-cloud-starter-gateway-server-webmvc` (check the artifact name for your Spring Cloud version) and do not include the WebFlux starter in the same module.
- Routes can be defined in YAML or with the Java `RouterFunction` DSL; custom filters use `HandlerFilterFunction` or plain servlet `Filter`.
- JWT verification uses Spring Security Resource Server (`SecurityFilterChain`).

---

## 6. CI/CD (GitHub Actions)

Workflow `.github/workflows/deploy.yml`:
1. Trigger: push to `main`
2. Build each service image (matrix) and push to GHCR
3. SSH into the VPS with `appleboy/ssh-action` and run:
   ```bash
   docker compose pull && docker compose up -d --remove-orphans
   ```
4. Secrets: `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`

Recommendation: use a dedicated deploy user with its own SSH key, not root.

---

## 7. Monitoring & Observability

- Add `spring-boot-starter-actuator` and `micrometer-registry-prometheus` to each service
- Prometheus scrapes `/actuator/prometheus`
- Log to stdout; Promtail ships to Loki; view everything in Grafana
- Tracing: OpenTelemetry Java agent + Jaeger/Zipkin (do this last)

---

## 8. Demo Scenarios

- **Load and stock integrity:**
  - k6 or JMeter script, 500–1000 requests/s against a SKU with small stock (for example 10)
  - Prove: number of successful orders equals the stock, and stock never goes negative
  - Capture the Grafana dashboard during the test
- **Availability:**
  - `docker stop notification-service`; placing orders still succeeds
  - Start it again; the emails are sent from the queue backlog
- Prepare: seed data, Swagger UI for each service, API documentation written before code (API-first)

---

## 9. Suggested Order of Work

1. Set up the parent POM, `common-lib`, and a local docker-compose with the infrastructure
2. Identity Service and Gateway (login, JWT, routing)
3. Product Service (Mongo first, then Elasticsearch) and gRPC
4. Order & Inventory with Redis lock and outbox
5. RabbitMQ, Payment, Notification; complete the Saga and compensation
6. Load testing, fix race conditions
7. Dockerfile per service, VPS, Nginx, SSL, CI/CD
8. Monitoring, report, slides

---

## 10. Checklist Against the Grading Criteria

- [ ] At least 4 microservices running independently in containers
- [ ] At least 1 asynchronous flow through RabbitMQ/Kafka
- [ ] Running on a real VPS with domain + HTTPS and a safe firewall
- [ ] Load demo with consistent stock
- [ ] Demo of stopping the Notification Service while ordering still works
