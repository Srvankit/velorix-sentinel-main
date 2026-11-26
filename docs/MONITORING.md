# Velorix Sentinel — Telemetry, Monitoring & Health Architecture

This document specifies the operational telemetry, metric catalog, health check indicators, alert thresholds, and log audit patterns for Velorix Sentinel staging and production environments.

---

## 1. Metric Catalog & Telemetry Inventory

Metrics are exposed via Spring Boot Actuator and Micrometer. The Prometheus scraping endpoint `/actuator/prometheus` provides quantitative runtime metrics.

### 1.1 Custom Application Meters (Packet Inspection Engine)

Defined and registered in `com.velorix.sentinel.config.InspectionExecutorConfig`:

| Metric Name | Type | Description | Source Reference |
| :--- | :--- | :--- | :--- |
| `inspection.executor.active` | Gauge | Number of packet analysis worker threads currently executing. | `InspectionExecutorConfig.java:58` |
| `inspection.executor.pool.size` | Gauge | Current number of worker threads allocated in the inspection thread pool. | `InspectionExecutorConfig.java:62` |
| `inspection.executor.queue.depth` | Gauge | Number of PCAP inspection tasks waiting in the queue for an available worker thread. | `InspectionExecutorConfig.java:66` |
| `inspection.executor.queue.capacity` | Gauge | Maximum queue capacity before rejection (configured by `velorix.inspection.executor.queue-capacity`, default `50`). | `InspectionExecutorConfig.java:70` |

### 1.2 Platform & Infrastructure Meters

| Metric Name | Type | Description | Critical Tags |
| :--- | :--- | :--- | :--- |
| `http.server.requests` | Timer | Request rate, duration, and HTTP status codes. | `uri`, `status`, `method`, `exception` |
| `hikaricp.connections.active` | Gauge | Database connections currently executing queries. | `pool="HikariPool-1"` |
| `hikaricp.connections.idle` | Gauge | Database connections open and waiting in the pool. | `pool="HikariPool-1"` |
| `hikaricp.connections.pending` | Gauge | Threads blocked waiting for a database connection. | `pool="HikariPool-1"` |
| `hikaricp.connections.max` | Gauge | Maximum allowed pool size (configured to `10` for Supabase session pooler). | `pool="HikariPool-1"` |
| `jvm.memory.used` | Gauge | JVM memory currently in use (bytes). | `area="heap"`, `area="nonheap"` |
| `jvm.memory.max` | Gauge | Maximum JVM memory limit available to the container. | `area="heap"` |
| `jvm.gc.pause` | Timer | Garbage collection pause duration and frequency. | `action`, `cause` |
| `jvm.threads.live` | Gauge | Current live thread count. | — |
| `process.uptime` | TimeGauge | Application process uptime in seconds. | — |

### 1.3 Metric Gaps (Explicitly Documented)

The following metrics are **not** currently emitted as custom Micrometer meters; operational monitoring relies on database queries or log patterns:

- **`[GAP] inspection.duration`**: No custom Micrometer timer measures analysis execution time.
  - *Mitigation:* Wall-clock duration is persisted per analysis in PostgreSQL (`analyses.duration_ms`). Query [`BETA_OPS_QUERIES.sql`](BETA_OPS_QUERIES.sql) (Query 3) for p50/p90/p99 latency percentiles.
- **`[GAP] auth.failures`**: No custom Micrometer counter tracks failed login attempts.
  - *Mitigation:* Failed sign-in events are persisted to PostgreSQL (`activity_logs` where `activity_type = 'LOGIN_FAILURE'`). Query [`BETA_OPS_QUERIES.sql`](BETA_OPS_QUERIES.sql) (Query 5).
- **`[GAP] rate_limit.rejected`**: No custom Micrometer counter counts Bucket4j rate-limit rejections.
  - *Mitigation:* Track HTTP 429 occurrences in `http.server.requests` where tag `status="429"`.
- **`[GAP] storage.errors`**: No custom Micrometer counter counts S3/Supabase storage upload/read failures.
  - *Mitigation:* `StorageHealthIndicator` surfaces degraded storage health in `/actuator/health/readiness`, and failures trigger `ERROR` logs.

---

## 2. Health Indicators & Probes

Health checks are managed via Spring Boot Actuator and categorized into Liveness and Readiness groups.

### 2.1 Endpoint Summary

- **Overall Health:** `GET /actuator/health` (Summarizes all indicators; details restricted by security configuration)
- **Liveness Probe:** `GET /actuator/health/liveness` (Determines whether container is alive or needs a restart)
  - Components: `ping`, `livenessState`
- **Readiness Probe:** `GET /actuator/health/readiness` (Determines whether container is ready to accept user traffic)
  - Components: `db`, `diskSpace`, `inspectionQueue`, `storage`, `readinessState`

### 2.2 Custom Health Indicators

1. **`InspectionQueueHealthIndicator` (`com.velorix.sentinel.health.InspectionQueueHealthIndicator`)**:
   - Status: `UP` when queue depth is within operational limit.
   - Status: `OUT_OF_SERVICE` when queue depth reaches capacity, preventing further task queuing.
   - Contributes to readiness probe.
2. **`StorageHealthIndicator` (`com.velorix.sentinel.health.StorageHealthIndicator`)**:
   - Status: `UP` when storage provider (S3 or filesystem) is reachable and readable/writable.
   - Status: `DOWN` when storage provider cannot be accessed.
   - Contributes to readiness probe.

---

## 3. Solo Owner Free-Tier Alert Thresholds

Configured for operation on free-tier infrastructure:
- **Render Web Service (Free Tier):** 512MB RAM, shared CPU. Spins down after 15 minutes of inactivity unless kept warm.
- **Supabase PostgreSQL (Free Tier):** 500MB database storage, maximum 10 connection pooler limit.

| Metric / Signal | Warning Threshold | Critical Threshold | Root Cause / Recommended Action |
| :--- | :--- | :--- | :--- |
| **JVM Memory Usage** | > 75% (~384 MB) | > 85% (~435 MB) | Container approaching Render 512MB limit; riesgo of OOM kill. Inspect heap dump; reduce file upload batch sizes. |
| **Inspection Queue Depth** | > 5 tasks | > 15 tasks (capacity 50) | Queue backlog building up. Verify if worker threads are hung on large or malformed PCAPs. Check reaper. |
| **DB Active Connections** | > 7 active | > 9 active (max 10) | Connection pool saturation. Check for unclosed transactions or slow queries in Supabase dashboard. |
| **HTTP 5xx Error Rate** | > 1% over 5m | > 5% over 5m | Unhandled backend exceptions. Check Render logs for stack traces. |
| **HTTP 429 Rate Limit Hits** | > 10 / min | > 50 / min | Client retry storm or brute-force attempt. Investigate originating IP in `activity_logs`. |
| **Readiness Probe** | DOWN > 1 min | DOWN > 3 min | Backend cannot connect to database or storage. Triage Supabase availability and Render networking. |
| **Reaper Stuck Analyses** | > 0 for > 15m | > 2 for > 30m | Inspections not completing within `INSPECTION_MAX_DURATION` (15m). Run Reaper query in [`BETA_OPS_QUERIES.sql`](BETA_OPS_QUERIES.sql). |

---

## 4. Prometheus Endpoint Security

The Prometheus metrics endpoint (`/actuator/prometheus`) is **strictly secured** by Spring Security.

- **Security Rule:** Configured in `com.velorix.sentinel.config.SecurityConfig:101`:
  ```java
  .requestMatchers("/actuator/prometheus").hasRole("ADMIN")
  ```
- **Access Control:**
  - Anonymous requests receive HTTP `401 Unauthorized`.
  - Non-admin authenticated users (`ROLE_USER`, `ROLE_ANALYST`) receive HTTP `403 Forbidden`.
  - Only authenticated requests bearing a valid JWT with `ROLE_ADMIN` are permitted.
  - Verified by `com.velorix.sentinel.security.PrometheusEndpointSecurityIntegrationTest`.
- **Operational Scraping / Verification via CLI:**
  ```bash
  # Step 1: Obtain Admin JWT
  ADMIN_TOKEN=$(curl -s -X POST https://api.staging.velorix.net/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@velorix.tech","password":"<SECURE_PASSWORD>"}' | jq -r '.accessToken')

  # Step 2: Scrape Prometheus Metrics
  curl -s -H "Authorization: Bearer ${ADMIN_TOKEN}" https://api.staging.velorix.net/actuator/prometheus
  ```

---

## 5. Structured JSON Logging & Search Patterns

The backend uses Logback with Logstash JSON formatting (`net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder`) in `logback-spring.xml`.

### 5.1 Sensitive Data Redaction & Sanitization

- **Email Addresses:** Masked using `com.velorix.sentinel.util.EmailLogSanitizer.maskEmail` (e.g., `t***1@example.com`). Cleartext emails are never output to application logs.
- **Passwords & Tokens:** BCrypt hashes, JWTs, and verification tokens are never logged in cleartext (verified in `com.velorix.sentinel.service.LoggingEmailServiceTest`).
- **Database Connection Strings:** JDBC query parameters containing passwords or tokens are scrubbed to `?[REDACTED]`.

### 5.2 Key Search Patterns in Render / Log Drain

When investigating incidents or monitoring the beta in Render log streams, filter with these patterns:

| Investigation Purpose | Search Pattern / Filter | Expected Output / Meaning |
| :--- | :--- | :--- |
| **Invite-Only Rejections** | `Invite-only registration rejected` | Logs masked email of uninvited user attempting password registration. |
| **Invite-Only OAuth Blocks** | `Invite-only OAuth registration rejected` | Logs masked email of uninvited user attempting OAuth login. |
| **Startup Beta Gate Status** | `Closed beta invite-only gate:` | Emits `ON` or `OFF` and number of configured entries at startup. |
| **Reaper Sweeps** | `InspectionReaper` | Sweeps for analyses stuck in `PROCESSING` beyond 15m timeout. |
| **Database Connection Errors** | `HikariPool` OR `Connection refused` | DB pool starvation, SSL failure, or Supabase pooler outage. |
| **Storage Failures** | `StorageException` OR `S3Exception` | File capture upload or retrieval failures. |
| **Production Guardrail Warnings** | `EMERGENCY OVERRIDE WARNING` | Staging running with filesystem storage or logging email fallback. |

---

## 6. Manual Steps for Owner (External Setup)

Because Render and external uptime monitoring providers require owner dashboard actions, follow these steps to configure monitoring for the closed beta:

### 6.1 Uptime Monitoring (Keep-Alive & Alerting)

1. Log into [UptimeRobot](https://uptimerobot.com) or [BetterStack](https://betterstack.com) (free tiers).
2. Create a new HTTP(s) Monitor:
   - **Monitor Type:** `HTTP(s)`
   - **Friendly Name:** `Velorix Staging Backend Readiness`
   - **URL:** `https://<STAGING_RENDER_SERVICE_NAME>.onrender.com/actuator/health/readiness`
   - **Monitoring Interval:** `5 minutes` (This also prevents Render free-tier instance from sleeping due to inactivity).
   - **Alert Contacts:** Add your email / SMS.
   - **Expected Status Code:** `200`
3. Create a second monitor for the frontend:
   - **URL:** `https://<STAGING_NETLIFY_SITE_NAME>.netlify.app`
   - **Interval:** `5 minutes`

### 6.2 Render Log Drain (Optional Alert Integration)

1. Navigate to the Render Dashboard > Staging Backend Web Service > **Settings**.
2. Scroll to **Log Streams**.
3. Add a log drain (e.g., BetterStack Logs / Papertrail / Datadog free tier) with token endpoint.
4. Set up an alert rule in the log provider for pattern: `level:ERROR` or `ProductionConfigurationException`.
