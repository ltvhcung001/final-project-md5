# Deploy to AWS: step by step

This guide takes you from "nothing" to "the system is running on my domain with HTTPS", with the fewest moving parts:
one EC2 server, Docker Compose, Nginx, and GitHub Actions for automatic deploys. No Terraform needed.

**How it fits together**

```
git push main ──> GitHub Actions: test, build 6 images, push to GHCR ──> SSH into the server: docker compose pull && up
                                                                                    │
browser ──HTTPS──> your domain (DNS A record) ──> Nginx (443) ──> API gateway (127.0.0.1:8080) ──> 6 services + databases
```

Time needed: about 45 minutes, plus 10-15 minutes for the first build.
More background (memory sizing, monitoring, troubleshooting) is in [report.md](report.md), section 7.

---

## 0. Before you start

| You need | Notes |
|---|---|
| AWS account | A free-tier or credits account is fine. Read the sizing note in step 1 first |
| A domain you own | Any registrar. You only need to add DNS records |
| The code on GitHub | A private or public repo, default branch `main`. Docker images are published to GitHub's registry (GHCR) |
| `ssh` and `scp` on your laptop | Included with Git for Windows, macOS and Linux |
| `jq` on your laptop (optional) | Only for the seed script in step 6 |

Values used below (replace with yours):

| Placeholder | Example |
|---|---|
| `<IP>` | the server's Elastic IP, for example `54.12.34.56` |
| `<DOMAIN>` | `example.com` (the API will live at `api.example.com`) |
| `<KEY>` | your key file, for example `omnichannel.pem` |

---

## 1. Create the server (AWS console, once)

> **Size matters.** The system needs **at least 4 GiB of RAM** (6 Java services plus databases). A 1 GiB `t2/t3.micro`
> cannot run it. In the launch wizard pick an instance type labelled **"Free tier eligible"** with 4 GiB or more if your
> account offers one. If not, see report.md section 7.1 for the options and the cost. Credits expire, so stop or delete
> the instance when you are not using it.

1. **EC2 -> Launch instance**
   - Name: `omnichannel`
   - Image: **Ubuntu Server 24.04 LTS**, architecture **64-bit (x86)** (not Arm/Graviton)
   - Instance type: 4 GiB RAM or more
   - Key pair: **Create new key pair**, type RSA, format `.pem`. Save the file, you cannot download it again
   - Network settings -> **Create security group**, and tick:
     - Allow SSH traffic from **My IP**
     - Allow HTTPS traffic from the internet
     - Allow HTTP traffic from the internet
   - Storage: **30 GiB**, gp3
   - Launch
2. **Give it a fixed address:** EC2 -> Elastic IPs -> **Allocate Elastic IP address** -> Allocate -> select it -> Actions ->
   **Associate** -> choose your instance. This is your `<IP>`. Without it the address changes on every restart.
3. **Do not open any other port** (not 5432, 27017, 6379, 5672, 15672, 8080...). Only 22, 80 and 443.

On your laptop, protect the key file (Git Bash / macOS / Linux): `chmod 400 <KEY>`

---

## 2. Point your domain to the server

At the place where you manage DNS for your domain (your registrar, Cloudflare, or Route 53), add this record:

| Type | Name / Host | Value | TTL |
|---|---|---|---|
| **A** | `api` | `<IP>` | Auto / 300 |
| A *(only if you will use Grafana)* | `grafana` | `<IP>` | Auto / 300 |

Where to click:

- **Namecheap / GoDaddy / most registrars:** Domain -> DNS / Manage DNS -> Add record. Use only `api` as the host, not the full name.
- **Cloudflare:** DNS -> Add record. Set the proxy status to **DNS only** (grey cloud), at least until the certificate is issued.
- **AWS Route 53:** Hosted zones -> your domain -> Create record -> simple routing. (A hosted zone costs about $0.50/month,
  and your registrar's name servers must point to it.)

Check that it works (wait a few minutes if it does not):

```bash
nslookup api.<DOMAIN>          # must show <IP>
```

> No domain yet? Use `api.<IP-with-dashes>.sslip.io`, for example `api.54-12-34-56.sslip.io`. It resolves to the IP
> automatically, so no DNS record is needed. Use `54-12-34-56.sslip.io` as `<DOMAIN>` everywhere.

---

## 3. Prepare the server (once)

Run these from the repo folder on your laptop.

```bash
# 1) create the folder and copy the deployment files up
ssh -i <KEY> ubuntu@<IP> "mkdir -p ~/omnichannel"
scp -i <KEY> -r deploy ubuntu@<IP>:~/omnichannel/

# 2) install Docker, Nginx, Certbot, swap, firewall
ssh -i <KEY> ubuntu@<IP> "bash ~/omnichannel/deploy/aws/bootstrap-ec2.sh"

# 3) log in again (so the docker group applies) and create the configuration file
ssh -i <KEY> ubuntu@<IP>
bash ~/omnichannel/deploy/aws/init-env.sh
```

`init-env.sh` asks for three things (domain, your GitHub user/org name, admin email), generates strong random passwords
and secrets, and **prints the admin password once**. Copy it somewhere safe.

If the machine has under 6 GB RAM it also lowers the Java memory automatically.

Optional settings you can edit later with `nano ~/omnichannel/deploy/.env` (see the table at the end of this file):
SMTP login for real emails, VNPay/MoMo sandbox keys.

---

## 4. HTTPS (free certificate)

Still on the server:

```bash
bash ~/omnichannel/deploy/aws/setup-https.sh
```

The script checks that `api.<DOMAIN>` really points to this server, installs the Nginx site, gets the Let's Encrypt
certificate, turns on the HTTP to HTTPS redirect and tests automatic renewal. If DNS is not ready it tells you and stops;
fix the record and run it again. Opening `https://api.<DOMAIN>` now shows `502 Bad Gateway`. That is expected until the
application is deployed.

---

## 5. Automatic deployment with GitHub Actions

1. Push the code to GitHub (`main` branch).
2. In the repo: **Settings -> Secrets and variables -> Actions -> New repository secret**. Add three secrets:

   | Name | Value |
   |---|---|
   | `VPS_HOST` | the Elastic IP `<IP>` |
   | `VPS_USER` | `ubuntu` |
   | `VPS_SSH_KEY` | the **entire contents** of the `.pem` file, including the `BEGIN` and `END` lines |

3. Start the first deployment: push any commit to `main`, or open the **Actions** tab -> **Build and Deploy** -> **Run workflow**.

What the workflow does: runs the unit tests, builds the six Docker images, pushes them to `ghcr.io/<you>/omnichannel-<service>`,
copies the `deploy/` files to the server, logs the server in to the registry with a temporary token, and runs
`docker compose pull` and `up -d`. The first run takes 10-15 minutes. Every later push to `main` redeploys automatically.

If the deploy step says "denied" when pulling images: GitHub -> your profile -> Packages -> each
`omnichannel-*` package -> Package settings -> change visibility to **Public** (or connect it to the repository).

---

## 6. Check that it works

Give the services 1-2 minutes after the deploy, then:

```bash
# on the server
docker compose -f ~/omnichannel/deploy/docker-compose.production.yml ps        # everything "healthy" / "running"

# from your laptop
curl -s https://api.<DOMAIN>/api/products                                       # {"code":0,...}
BASE=https://api.<DOMAIN> ADMIN_EMAIL=<your admin email> ADMIN_PASSWORD='<admin password>' bash scripts/seed.sh
```

The seed script creates a category, a brand, three products and their stock. After that:

- Log in as admin: `POST https://api.<DOMAIN>/api/auth/login` with the admin email and password.
- To click through the API in a browser, set `SWAGGER_ENABLED=true` in `~/omnichannel/deploy/.env`, run
  `docker compose -f ~/omnichannel/deploy/docker-compose.production.yml up -d`, and open
  `https://api.<DOMAIN>/swagger-ui.html`. Turn it off again after the demo.
- The `MOCK` payment method works out of the box, so you can place and "pay" orders without VNPay or MoMo.

Check the firewall from your laptop: only ports 22, 80 and 443 should answer (`nmap -Pn <IP>`).

---

## 7. Day to day

| I want to... | Do this |
|---|---|
| Deploy a change | `git push` to `main`. Wait for the Actions run |
| See logs | `docker compose -f ~/omnichannel/deploy/docker-compose.production.yml logs -f --tail=100 order-inventory-service` |
| Restart one service | `docker compose -f ~/omnichannel/deploy/docker-compose.production.yml restart payment-service` |
| Change configuration | `nano ~/omnichannel/deploy/.env`, then `docker compose -f ~/omnichannel/deploy/docker-compose.production.yml up -d` |
| Check memory | `free -h` and `docker stats --no-stream` |
| Save money between demos | EC2 console -> Instance state -> **Stop**. Start it again later; the Elastic IP and data stay, and the containers come back by themselves |
| Remove everything | Terminate the instance, delete the volume, **release the Elastic IP**, delete the key pair. Add a budget alert in AWS Billing |

Tip: add `alias dc='docker compose -f ~/omnichannel/deploy/docker-compose.production.yml'` to `~/.bashrc` to type `dc ps`.

---

## 8. Optional extras

- **Real payments (sandbox):** put `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` (and the `MOMO_*` values) in `.env`, redeploy,
  and register `https://api.<DOMAIN>/api/payments/ipn/vnpay` (MoMo: `.../ipn/momo`) in the provider's portal. Use
  `"paymentMethod": "VNPAY"` when placing orders. Set `PAYMENT_MOCK_ENABLED=false` when you no longer want the fake method.
- **Real emails:** create a free Mailtrap inbox and put its SMTP host, port, user and password in `.env`
  (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`).
- **Monitoring (needs 8 GiB RAM):** add the `grafana` DNS record, run
  `docker compose -f ~/omnichannel/deploy/docker-compose.production.yml --profile monitoring up -d`, then
  `WITH_GRAFANA=yes bash ~/omnichannel/deploy/aws/setup-https.sh`. Open `https://grafana.<DOMAIN>` (user `admin`, password in `.env`).
- **Elasticsearch search (needs about 1 GB more):** `--profile search` and `SEARCH_ENABLED=true` in `.env`.
- **Load test on the server:** temporarily set `RATE_LIMIT_RPM=1000000` in `.env`, redeploy, run k6 from another machine,
  then set it back to `120`.

---

## 9. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `setup-https.sh` says DNS is not ready | The A record is missing or still propagating. Re-check with `nslookup api.<DOMAIN>`, wait, run again |
| Certbot fails | Port 80 closed in the AWS security group, or Cloudflare proxy (orange cloud) is on |
| Browser shows `502 Bad Gateway` | The gateway container is not up yet, or the first deploy has not run. Run `dc ps` and `dc logs api-gateway` |
| A service keeps restarting or shows `Killed` | Out of memory. Lower `JAVA_XMX` in `.env`, make sure swap is on (`free -h`), stop optional profiles |
| Actions fails at "Pull and restart" with `denied` | Make the GHCR packages public (step 5), or re-run the workflow |
| Actions fails at the SSH step | Wrong `VPS_HOST`/`VPS_SSH_KEY`, or the instance is stopped. Test with `ssh -i <KEY> ubuntu@<IP>` |
| `.env` errors such as "variable is not set" | Re-run `init-env.sh` (delete `.env` first) or fill in the missing value |
| Orders stay `PENDING` | Payment service not receiving events: `dc logs payment-service`, and check RabbitMQ with `dc exec rabbitmq rabbitmqctl list_queues` |
| Emails do not arrive | SMTP values wrong. `dc logs notification-service`; failed messages wait in the queue `notification.events.dlq` |
| `bash\r: No such file` when running a script | The file got Windows line endings. This repo forces LF for `*.sh`; re-copy the `deploy` folder |

---

## `.env` reference (`~/omnichannel/deploy/.env`)

| Variable | Meaning | Set by |
|---|---|---|
| `DOMAIN` | your domain, without `api.` | `init-env.sh` |
| `GHCR_OWNER` | GitHub user or org that owns the images (lowercase) | `init-env.sh` |
| `IMAGE_TAG` | image version, `latest` by default | default |
| `JWT_SECRET` | secret that signs login tokens. Keep private, changing it logs everyone out | generated |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first admin account, created on first start | `init-env.sh` |
| `*_DB_PASSWORD`, `POSTGRES_ADMIN_PASSWORD`, `RABBITMQ_*`, `GRAFANA_ADMIN_PASSWORD` | infrastructure passwords | generated |
| `JAVA_XMX` | max memory per Java service (`256m`, or `192m` on small servers) | auto |
| `PAYMENT_MOCK_ENABLED` | `true` enables the fake MOCK payment method | default `true` |
| `SWAGGER_ENABLED` | `true` serves the Swagger UI on the API domain | default `false` |
| `SEARCH_ENABLED` | `true` uses Elasticsearch | default `false` |
| `RATE_LIMIT_RPM` | requests per minute per client IP | default `120` |
| `VNPAY_*`, `MOMO_*` | payment sandbox credentials | you |
| `MAIL_*` | SMTP server for emails | you |

Never commit the `.env` file. It is already excluded by `.gitignore`.
