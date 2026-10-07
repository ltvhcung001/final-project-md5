# Omnichannel E-Commerce Platform: Implementation Report and Runbook

This report covers what was built, how to run and test it, what was measured, and how to deploy it on an AWS free-tier account.
Specification: [`README.MD`](README.MD) and the course brief (`De_Tai_1_Omnichannel_ECommerce.md`).

---

## 1. What was built

Six Spring Boot microservices (Java 25, Spring Boot 3.5.6, virtual threads) in one Maven multi-module repo.

| Module | Port | Responsibility | Store |
|---|---|---|---|
| `api-gateway` | 8080 | Single entry point. JWT verification, per-IP rate limit, RBAC for admin routes, strips client-sent `X-User-*` headers and injects trusted `X-User-Id/Roles/Email` | none |
| `identity-service` | 8081 | Register, login, refresh-token rotation, logout, shipping addresses, roles, first-admin bootstrap | PostgreSQL |
| `product-service` | 8082, gRPC 9092 | Products with SKU variants, categories, brands, search (MongoDB; Elasticsearch optional), gRPC `GetProduct(s)` | MongoDB (+ Elasticsearch) |
| `order-inventory-service` | 8083 | Orders and stock (core). Idempotency key, Redis guard, atomic stock reserve, outbox, saga handlers, state machine, pending-order timeout job | PostgreSQL + Redis |
| `payment-service` | 8084 | Payment transaction per order, VNPay / MoMo / MOCK providers, signed IPN webhooks, refund, reconciliation, outbox | PostgreSQL |
| `notification-service` | 8085 | Consumes order status events, sends email, keeps a delivery log, retry + dead-letter queue | MongoDB |
| `common-lib` | - | `ApiResponse {code,message,data}`, error codes, exception handler, event DTOs, broker names, header constants | - |
| `proto-contracts` | - | `product.proto` (gRPC Order -> Product) | - |

Infrastructure: PostgreSQL (one instance, 3 databases, one user each), MongoDB, Redis, RabbitMQ, optional Elasticsearch, Prometheus + Grafana + Loki + Promtail (optional profile).

### The order saga (choreography)

```
Client -> POST /api/orders (Idempotency-Key)
  order-inventory: gRPC GetProducts (name + price snapshot)
                   -> Redis Lua guard (atomic decrement, never below 0)
                   -> PostgreSQL: UPDATE inventory ... WHERE available >= qty   (source of truth)
                   -> order PENDING + reservations + outbox(OrderCreated, StatusChanged)   [one transaction]
  outbox publisher -> RabbitMQ  order.created
  payment-service  : creates transaction + payment URL      (poll GET /api/payments/order/{id})
  customer pays (VNPay/MoMo redirect, or the MOCK endpoint in the sandbox)
  payment-service  : verified IPN -> outbox(PaymentSucceeded | PaymentFailed) -> RabbitMQ
  order-inventory  : success -> CONFIRMED, reserved stock committed
                     failure / 15 min timeout / customer cancel -> CANCELLED, stock released (compensation)
  every status change -> outbox -> RabbitMQ -> notification-service -> email
```

### Status against the grading checklist

| Requirement | Status | Evidence |
|---|---|---|
| At least 4 microservices running independently in containers | Done | 6 services, each with its own Dockerfile and compose entry. All six ran together in Docker during testing |
| At least 1 async flow through RabbitMQ | Done | OrderCreated -> payment, PaymentSucceeded/Failed -> order, StatusChanged -> notification. Tested end to end |
| Distributed transaction (Saga) with compensation | Done | Payment failure cancels the order and restores stock (verified: stock returned to the pre-order value) |
| Outbox pattern | Done | Order and payment services write events in the same DB transaction as the change |
| Idempotency | Done | Same `Idempotency-Key` returns the same order (verified). Payment results are idempotent per order |
| Load test with consistent stock | Done, run locally | k6: 10 units, 300-800 req/s: exactly 10 orders, 0 oversold, stock never negative (section 5) |
| Stop Notification Service, orders still work | Done, run locally | Order created while it was down. The email was delivered after restart from the queue (section 5) |
| Real VPS with domain, HTTPS, firewall | Prepared, **not executed** | I cannot reach your AWS account. Section 7 is the step-by-step |
| Monitoring (Prometheus, Grafana, Loki) | Config written, **not run** | Compose profile `monitoring`. No dashboards are provisioned yet (section 9) |
| Swagger UI | Done | One aggregated UI at the gateway: `http://localhost:8080/swagger-ui.html` (section 3.3) |

---

## 2. Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | 25 (you have `azul-25`) | building and running without Docker |
| Maven | 3.9+ | build |
| Docker Desktop | recent | the easiest way to run everything |
| `curl`, `jq` | any | seed script and API examples |
| Git, GitHub account | - | CI/CD |

---

## 3. Run it locally

### 3.1 Everything in Docker (recommended)

```bash
docker compose -f deploy/docker-compose.local.yml --profile apps up -d --build
```

- The first build takes several minutes (Maven downloads dependencies). Later builds are fast.
- Each service takes 30-45 s to start on a laptop. Wait until `http://localhost:8080/actuator/health` returns `UP`.
- Seed demo data (category, brand, 3 products, stock):

```bash
bash scripts/seed.sh
```

| URL | What |
|---|---|
| http://localhost:8080 | API gateway |
| http://localhost:8025 | Mailpit: every email the platform sends |
| http://localhost:15672 | RabbitMQ UI (guest / guest) |
| http://localhost:8080/swagger-ui.html | **Swagger UI for all services** (use the dropdown top-right); see 3.3 |

Default local admin: `admin@example.com` / `Admin@12345` (created by `identity-service` on startup). **Local use only.**

Stop everything: `docker compose -f deploy/docker-compose.local.yml --profile apps down` (add `-v` to wipe data).

### 3.2 From the IDE or the terminal (JDK 25)

```bash
docker compose -f deploy/docker-compose.local.yml up -d     # infrastructure only
mvn -DskipTests package
bash scripts/run-local.sh                                   # starts the six jars, logs in ./logs
bash scripts/seed.sh
bash scripts/stop-local.sh
```

> **Windows problem I hit on this machine:** every JVM failed with `IOException: Unable to establish loopback connection`
> (`Selector.open()` fails, on JDK 17, 21 and 25 alike, while Python and curl on loopback work). Spring Boot cannot start
> there. Likely causes are a broken Winsock layer or a security/VPN product hooking the network stack. Running in
> Docker avoids it. If you hit the same error natively, try (as administrator) `netsh winsock reset`, reboot, and
> disable any VPN or endpoint-security network filter. I did not verify that fix.

### 3.3 Test the API from Swagger UI

Open **http://localhost:8080/swagger-ui.html**. The dropdown at the top right switches between identity, product, order and payment.

1. Choose `identity`, open `POST /api/auth/login`, click **Try it out**, and use `{"email":"admin@example.com","password":"Admin@12345"}` (or register a customer with `/api/auth/register`).
2. Copy `data.accessToken` from the response.
3. Click **Authorize** (top right), paste the token (without the word `Bearer`) and confirm. It is remembered across page reloads, and it applies to all four services.
4. Now try any endpoint, for example `order` -> `POST /api/orders` (give any `Idempotency-Key`), then `payment` -> `GET /api/payments/order/{orderId}` and `POST /api/payments/mock/{orderId}/complete`.

Admin-only endpoints (products, inventory) need the admin token, and a customer token gets 403. The internal `X-User-*` headers are hidden because the gateway sets them. On the public server the UI is off by default; set `SWAGGER_ENABLED=true` in `deploy/.env` when you want it for a demo.

### 3.4 Optional Elasticsearch

```bash
docker compose -f deploy/docker-compose.local.yml --profile search --profile apps up -d
# then set SEARCH_ENABLED=true and ELASTICSEARCH_URIS=http://elasticsearch:9200 on product-service
```

Without it, `GET /api/products?q=` falls back to a case-insensitive name search in MongoDB. This is the default so that small servers can run the stack.

---

## 4. API tour

All responses use `{ "code": 0, "message": "success", "data": ... }`. Errors use a non-zero `code` (see `ErrorCode.java`).

HTTP status codes: creating something (register, place order, create product/category/brand/address) returns **201 Created**
(with a `Location` header for orders, products, categories and brands). Deleting returns **204 No Content** with no body.
Everything else successful is **200**. Errors keep their own status (400, 401, 403, 404, 409, 502, 503).

```bash
B=http://localhost:8080; H='Content-Type: application/json'

# customer
TOKEN=$(curl -s -X POST $B/api/auth/register -H "$H" \
  -d '{"email":"me@example.com","password":"Passw0rd!","fullName":"Me"}' | jq -r .data.accessToken)

curl -s "$B/api/products?q=omni" | jq .data.items          # public search

# place an order (the Idempotency-Key header is mandatory; retries with the same key are safe)
curl -s -X POST $B/api/orders -H "$H" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: my-key-1" -d '{
  "items":[{"sku":"PHONE-001","quantity":1}],
  "shipping":{"receiverName":"Me","phone":"0900000000","address":"1 Test St"},
  "paymentMethod":"MOCK"}' | jq .data

curl -s $B/api/payments/order/<orderId> -H "Authorization: Bearer $TOKEN" | jq .data     # payment URL appears after ~1 s
curl -s -X POST "$B/api/payments/mock/<orderId>/complete?success=true" -H "Authorization: Bearer $TOKEN"
curl -s $B/api/orders/<orderId> -H "Authorization: Bearer $TOKEN" | jq .data.status       # CONFIRMED
```

| Method and path | Who | Notes |
|---|---|---|
| `POST /api/auth/register \| login \| refresh \| logout` | public | Refresh tokens are rotated and stored hashed |
| `GET/POST/DELETE /api/users/me/addresses`, `GET /api/users/me` | customer | |
| `PUT /api/users/{id}/roles` | admin | |
| `GET /api/products`, `/api/products/{id}`, `/api/categories`, `/api/brands` | public | `q`, `categoryId`, `brandId`, `page`, `size` |
| `POST/PUT/DELETE /api/products`, `/api/categories`, `/api/brands` | admin | gateway enforces; delete is a soft delete |
| `PUT/GET /api/inventory/{sku}` | admin | sets sellable quantity (DB and Redis) |
| `POST /api/orders` (+ `Idempotency-Key`) | customer | `paymentMethod`: `VNPAY`, `MOMO`, `MOCK` |
| `GET /api/orders`, `GET /api/orders/{id}`, `POST /api/orders/{id}/cancel` | customer | only PENDING orders can be cancelled |
| `PUT /api/orders/{id}/status` | admin | CONFIRMED -> SHIPPING -> COMPLETED |
| `GET /api/payments/order/{orderId}` | customer / admin | payment status and URL |
| `POST /api/payments/mock/{orderId}/complete?success=` | customer | sandbox only (`PAYMENT_MOCK_ENABLED=true`) |
| `GET /api/payments/ipn/vnpay`, `GET /api/payments/return/vnpay`, `POST /api/payments/ipn/momo` | provider callbacks | open at the gateway, protected by signature |
| `POST /api/payments/{orderId}/refund`, `GET /api/payments/reconciliation?date=` | admin | |

### Real VNPay / MoMo sandbox

1. Register for the VNPay sandbox and MoMo test environment and put the credentials into `deploy/.env` (`VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `MOMO_*`).
2. Use `"paymentMethod":"VNPAY"`. The `paymentUrl` returned by `GET /api/payments/order/{id}` is the page to open.
3. The provider must reach your IPN URL, so use the public HTTPS domain (section 7). On localhost only the browser return URL works. It applies the same idempotent, signature-checked handler.
4. The VNPay and MoMo signing code is covered by unit tests, but I could **not** test against the real sandboxes (no credentials). Expect to adjust field details on first contact.
5. The refund endpoint records the refund in our database. Calling the provider's refund API is not implemented.

---

## 5. Tests and demos (what I ran and the numbers)

### 5.1 Unit tests

```bash
mvn test        # 11 tests
```

Covered: order state machine, VNPay signature (round trip, tampering rejected), payment idempotency and refund rules, and the gateway filter that strips spoofed `X-User-*` headers.

### 5.2 Flash-sale load test (stock integrity)

`scripts/loadtest/flash-sale.js` sends orders for one SKU with 10 units, using 50 users and unique idempotency keys. It **fails** unless exactly 10 orders succeed and stock ends at `available=0`.

```bash
# k6 inside the compose network (most accurate on Windows/macOS)
docker run --rm -i --network deploy_default -e BASE=http://api-gateway:8080 -e RATE=500 -e DURATION=20s \
  -v "$PWD/scripts/loadtest:/scripts" grafana/k6 run /scripts/flash-sale.js
```

On Git Bash for Windows prefix the command with `MSYS_NO_PATHCONV=1`. The gateway must run with a high `RATE_LIMIT_RPM` (the local compose already sets it).

Measured on a laptop (8 CPUs given to Docker, 256 MB heap per service, k6 on the same machine):

| Target rate | Orders created | Rejected "out of stock" | Unexpected errors | Latency (p95) | Stock check |
|---|---|---|---|---|---|
| 300 req/s (15 s) | **10** | thousands | 0 | 470 ms | pass |
| 800 req/s target (20 s) | **10** | 10,073 | 0 | 3.7 s | pass; sustained ~380 req/s |
| 500 req/s via the Docker Desktop host port | **10** | 2,665 | 0 | slow | pass (the Windows host-port path added a lot of latency) |

The proven property is correctness: exactly the stock count of orders succeeded every time, and stock never went negative.
The raw throughput ceiling on this laptop was about 380 req/s, so "500-1000 req/s sustained" needs a bigger machine or more heap and CPU.
Do the final demo on the server and say so honestly in the slides.

### 5.3 Availability demo (Notification down)

```bash
docker stop deploy-notification-service-1          # place an order -> still succeeds (PENDING)
docker exec deploy-rabbitmq-1 rabbitmqctl list_queues name messages    # the message waits in notification.events
docker start deploy-notification-service-1         # the email arrives in Mailpit
```

Result: the order succeeded while the service was down, the email appeared after restart, and the queue and dead-letter queue ended empty.

### 5.4 Saga checks I ran by hand

| Case | Result |
|---|---|
| Place order, same `Idempotency-Key` twice | same order id returned |
| Pay (MOCK) | order CONFIRMED, stock committed (500 -> 498) |
| Payment declined | order CANCELLED with reason, stock back to 498 |
| Customer calls an admin endpoint | 403 |
| Emails | "received", "confirmed", "cancelled" all in Mailpit |

---

## 6. Configuration reference (important variables)

| Variable | Where | Meaning |
|---|---|---|
| `JWT_SECRET` | gateway and identity (must match) | HS256 secret, at least 32 bytes. **Change it.** |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | identity | first admin account |
| `RATE_LIMIT_RPM` | gateway | requests per minute per IP (default 120) |
| `PAYMENT_MOCK_ENABLED` | payment | enables the fake "MOCK" provider |
| `SEARCH_ENABLED` | product | `true` uses Elasticsearch |
| `JAVA_XMX` | compose | max heap per Java service (default 256m) |
| `VNPAY_*`, `MOMO_*` | payment | sandbox credentials |
| `MAIL_HOST/PORT/USER/PASSWORD/AUTH/STARTTLS` | notification | SMTP |
| `order.pending-timeout` | order | unpaid orders are cancelled after this (default 15 min) |

Measured memory after the load test with `-Xmx256m`: gateway 440 MB, identity 255 MB, product 339 MB, order-inventory 618 MB, payment 295 MB, notification 201 MB (about 2.1 GB for the six services).

---

## 7. Deploy to AWS (free-tier account)

> **Short version:** follow [DEPLOY.md](DEPLOY.md), a copy-paste guide with helper scripts (`deploy/aws/init-env.sh` creates the
> configuration, `deploy/aws/setup-https.sh` sets up Nginx and HTTPS and checks your domain). This section keeps the background and options.

### 7.1 Choose the instance

The whole system needs real memory: the six Java services alone use about 2.1 GB, plus PostgreSQL, MongoDB, Redis and RabbitMQ (about 0.6 GB), plus the OS.

| Instance memory | Can it run the stack? |
|---|---|
| 1 GiB (`t2/t3.micro`, the classic 12-month free tier) | **No.** It cannot even hold the six Java services |
| 2 GiB (`t3.small`) | No. It only fits a partial subset (for example gateway, identity, order, payment, Redis, Postgres, RabbitMQ) |
| **4 GiB** (for example `c7i-flex.large`) | **Yes, tight.** Use `JAVA_XMX=192m`, 2-4 GB swap, no monitoring, no Elasticsearch |
| **8 GiB** (for example `m7i-flex.large`, or a paid `t3.large`) | Comfortable, also fits the `monitoring` profile |

Which instance types are free depends on **when your account was created**. I could not verify this on your account, so check in the console:

- Accounts created before 15 July 2025: the classic Free Tier gives 750 hours/month of `t2/t3.micro` for 12 months. That is too small for this project.
- Accounts created on or after 15 July 2025: the "Free plan" gives credits (about $100, more by completing activities) valid for up to 6 months, and a list of eligible instance types. At the time of writing that list included flex instances with 4-8 GiB. In the EC2 launch wizard, look for the "Free tier eligible" label and pick the largest eligible type with at least 4 GiB.
- Credits run out and the plan ends after 6 months. A 4 GiB instance costs roughly 60 USD/month at on-demand prices, so credits last a few months at best. **Stop the instance when you are not demoing** and delete everything at the end (7.9).
- If your account only offers 1 GiB instances, do not spend money on a bigger one for a course project without checking the price. Alternatives: demo from your own machine in Docker (section 3), or rent one small paid VPS for the demo day only.

### 7.2 Create the server

1. EC2 -> Launch instance. Name `omnichannel`. AMI **Ubuntu Server 24.04 LTS (x86_64)**. Choose the `x86_64` architecture. The CI images are built for amd64, so do **not** pick Graviton/`t4g`.
2. Instance type: from 7.1 (4 GiB minimum).
3. Key pair: create `omnichannel.pem` and keep it safe.
4. Storage: 30 GiB gp3 (30 GB is within the free allowance).
5. Security group (this is your firewall):
   - SSH 22: **source = My IP only**
   - HTTP 80: anywhere
   - HTTPS 443: anywhere
   - Nothing else. Do **not** open 5432, 27017, 6379, 5672, 15672, 9090, 3000 or 8080.
6. Allocate an **Elastic IP** and associate it with the instance, so the address survives stop/start. A public IPv4 address can cost a small hourly fee outside the free allowances. Release the IP when you delete the project.

### 7.3 Domain and DNS

You need hostnames for HTTPS. Free options:

- **sslip.io (zero setup):** if the Elastic IP is `54.12.34.56`, the names `api.54-12-34-56.sslip.io` and `grafana.54-12-34-56.sslip.io` resolve to it automatically. Use `DOMAIN=54-12-34-56.sslip.io`. Let's Encrypt can issue certificates for it, but shared services can hit rate limits.
- **Your own domain:** add A records `api.<domain>` and `grafana.<domain>` pointing to the Elastic IP. (Route 53 hosted zones are not free.)
- **DuckDNS** also works for free subdomains.

### 7.4 Prepare the server (once)

```bash
ssh -i omnichannel.pem ubuntu@<ELASTIC_IP>
curl -fsSL https://raw.githubusercontent.com/<you>/<repo>/main/deploy/aws/bootstrap-ec2.sh -o bootstrap.sh
SWAP_GB=4 bash bootstrap.sh      # swap, Docker, compose, Nginx, Certbot, UFW (22/80/443)
exit                             # log in again so the docker group applies
```

Create the environment file on the server (never commit it):

```bash
ssh -i omnichannel.pem ubuntu@<ELASTIC_IP>
nano ~/omnichannel/deploy/.env       # start from deploy/.env.example in the repo
```

Required values: `DOMAIN`, `GHCR_OWNER` (your GitHub user or org, lowercase), `JWT_SECRET` (`openssl rand -base64 48`), all `*_PASSWORD` values (use long random strings), `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`. On a 4 GiB machine also set `JAVA_XMX=192m`.
For email use a Mailtrap sandbox inbox (free): `MAIL_HOST=sandbox.smtp.mailtrap.io`, `MAIL_PORT=2525`, plus its user and password.

### 7.5 HTTPS with Nginx and Certbot

```bash
# on the server (replace the domain)
sudo cp ~/omnichannel/deploy/nginx/api.conf /etc/nginx/sites-available/omnichannel
sudo sed -i 's/DOMAIN/54-12-34-56.sslip.io/g' /etc/nginx/sites-available/omnichannel
sudo ln -s /etc/nginx/sites-available/omnichannel /etc/nginx/sites-enabled/omnichannel
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.54-12-34-56.sslip.io          # add -d grafana... only if you enable monitoring
```

(The first copy of `deploy/` reaches the server on the first CI run in 7.6. If you want to configure Nginx before that, `scp` the file.)
The Nginx config returns 404 for `/actuator`, so metrics stay private.

### 7.6 CI/CD with GitHub Actions

1. Push this repo to GitHub (default branch `main`).
2. Repository -> Settings -> Secrets and variables -> Actions, add:
   - `VPS_HOST`: the Elastic IP
   - `VPS_USER`: `ubuntu`
   - `VPS_SSH_KEY`: the full contents of `omnichannel.pem` (better: create a separate deploy key and put its public half in `~/.ssh/authorized_keys`)
3. Push to `main`. The workflow in `.github/workflows/deploy.yml` will:
   1. run the unit tests (`mvn verify` on JDK 25),
   2. build the six images in parallel and push them to GHCR (`ghcr.io/<owner>/omnichannel-<service>`),
   3. copy `deploy/` files to the server and run `docker compose pull && up -d --remove-orphans`, logging in to GHCR with the short-lived workflow token.
4. Image names must be lowercase. The workflow lowercases the owner for you. Keep `GHCR_OWNER` in `.env` lowercase too.

You can also deploy by hand on the server: `cd ~/omnichannel/deploy && docker compose -f docker-compose.production.yml up -d` (needs `docker login ghcr.io` with a personal access token that has `read:packages`, or make the packages public).

### 7.7 Verify the deployment

```bash
curl -s https://api.<DOMAIN>/actuator/health      # 404 by design (blocked by Nginx)
ssh ubuntu@<IP> "docker compose -f ~/omnichannel/deploy/docker-compose.production.yml ps"   # all healthy
curl -s "https://api.<DOMAIN>/api/products" | jq .code                                       # 0
BASE=https://api.<DOMAIN> ADMIN_EMAIL=<yours> ADMIN_PASSWORD=<yours> bash scripts/seed.sh   # demo data
```

Services need 1-2 minutes to become healthy after `up`. The first start is slower.

Firewall check from your laptop: `nmap -Pn <IP>` should show only 22, 80, 443 open. All databases, Redis and RabbitMQ must be unreachable.

### 7.8 Optional pieces

- **Monitoring** (needs 8 GiB): `docker compose -f docker-compose.production.yml --profile monitoring up -d`, add the grafana hostname to Certbot, then open `https://grafana.<DOMAIN>`. Data sources (Prometheus, Loki) are provisioned. **Dashboards are not**: import community dashboard IDs 4701 (JVM Micrometer) and 12900 (Spring Boot) in Grafana. Do not expose Prometheus or RabbitMQ publicly; use an SSH tunnel (`ssh -L 15672:localhost:15672 ...` needs the port bound, so add a temporary `127.0.0.1:` port mapping first).
- **Elasticsearch** (needs about 1 GB more): `--profile search` and `SEARCH_ENABLED=true`.
- **Load test on the server:** set `RATE_LIMIT_RPM=1000000` in `.env`, run `up -d`, and run k6 from your laptop or a second machine against `https://api.<DOMAIN>`. Set it back afterwards, since it disables the protection.
- **Real payments:** put VNPay/MoMo sandbox credentials in `.env`, and register `https://api.<DOMAIN>/api/payments/ipn/vnpay` (and the MoMo IPN URL) in the provider portals.

### 7.9 Cost control and teardown

- Stop the instance (EC2 -> Instance state -> Stop) between demos. Storage and the Elastic IP still cost a little.
- Final cleanup: terminate the instance, delete the EBS volume, release the Elastic IP, and delete unused snapshots and security groups.
- Set a billing alert: Billing -> Budgets -> create a budget of a few dollars with an email alert.

### 7.10 Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| A container restarts or is `Killed` | Out of memory. Lower `JAVA_XMX`, add swap (`free -h`), stop optional profiles |
| `docker compose pull` says "denied" | GHCR login failed or the package is private. Re-run the workflow, or log in with a PAT, or make the package public |
| Certbot fails | DNS not pointing to the Elastic IP yet, or port 80 closed in the security group |
| `502 Bad Gateway` from Nginx | gateway still starting (`docker compose logs api-gateway`) |
| Orders stay PENDING | payment-service not consuming: check `docker compose logs payment-service` and RabbitMQ queues |
| Orders fail with 503 | product-service gRPC unreachable (gateway ok, order-inventory cannot call `product-service:9092`) |
| No emails | SMTP variables wrong: see `docker compose logs notification-service`, then the `notification.events.dlq` queue |
| Everything slow | swap thrashing: the instance is too small for the load |

---

## 8. Design decisions worth explaining in the defence

- **Database per service.** No service reads another's database. Product data reaches Orders only by gRPC at order time (snapshot), and everything else by events.
- **Two-layer stock protection.** Redis Lua rejects excess requests cheaply. PostgreSQL `UPDATE ... WHERE available >= qty` is the final authority, so Redis problems cannot cause overselling, and if Redis is down the guard is skipped.
- **Outbox in Order and Payment.** The event is committed with the data, so a broker outage delays events instead of losing them. Consumers are idempotent, so at-least-once delivery is fine.
- **Gateway trust model.** Downstream services trust `X-User-*` headers only because the gateway deletes client-supplied ones. The services must never be exposed directly. The compose file only publishes the gateway, on `127.0.0.1`.
- **Failure handling.** Listeners retry (5 times for order and payment, 3 for notification) and then dead-letter into `*.dlq` queues instead of looping forever.
- **Virtual threads** (Java 25) are enabled in every service.

## 9. Known limitations and next steps

- Throughput: sustained about 380 req/s on a laptop. The 500-1000 req/s target needs a larger host and probably a Hikari pool and heap tuning. Measure on the real server.
- Not tested: VNPay/MoMo real sandboxes, AWS deployment, the monitoring stack, Elasticsearch search, Testcontainers-based integration tests.
- No Grafana dashboards are provisioned, and no OpenTelemetry/Jaeger tracing (the README marks it optional).
- Rate limiting is in memory per gateway instance (fine for one instance). Circuit breaker and retry on the gateway were not added (the README asks to verify Server MVC support first).
- Refund records the state only and does not call the provider.
- A late `PaymentSucceeded` for an order already cancelled by timeout is logged and ignored. A real system would auto-refund it.
- Secrets in `application.yml` defaults are for local use only. Production values come from `.env`.
- JWT uses a shared HS256 secret. Moving to an asymmetric key (identity signs, gateway verifies with the public key) is a good hardening step.
