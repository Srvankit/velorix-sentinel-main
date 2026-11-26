# Changelog

All notable changes to Velorix Sentinel are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0-beta] - 2026-11-26

Initial controlled beta release of Velorix Sentinel, a deterministic deep packet inspection and network threat detection platform.

### Added

#### Authentication & Account Lifecycle
- Email/password registration with BCrypt hashing (strength factor 12) and mandatory verification token delivery.
- JWT session management with cryptographically secure refresh token rotation and family-based revocation.
- Password recovery flow with immediate session revocation across all active devices.
- OAuth2 social login via Google and GitHub with state verification and CSRF protection.
- Self-service account deletion cascading across users, tokens, analyses, and stored capture objects.

#### Packet Ingestion & Parsing Engine
- Offline PCAP and PCAPNG file upload with magic-byte validation and SHA-256 integrity digest generation.
- Streaming pure-Java packet decoder supporting Ethernet II, IPv4, IPv6, TCP, UDP, ICMP, DNS, HTTP, and TLS handshakes.
- Memory-bounded streaming execution to prevent JVM OutOfMemory errors on large capture files.
- Thread-safe background execution queue with configurable worker capacity and Actuator health reporting.
- Stuck-analysis reaper scheduled task to automatically fail and recover orphaned analyses exceeding timeout limits.

#### Threat Detection & Forensics
- 13 deterministic threat detection rules covering TCP SYN floods, UDP/ICMP amplification, port scans, DNS anomalies/tunneling, cleartext credentials, TLS downgrade attacks, brute force, and C2 periodic beaconing.
- Transparent risk scoring algorithm (0–100 scale) with explicit scan state synchronization and severity badges.
- Forensic analyst dashboard featuring packet inspection views, frame summaries, protocol distribution charts, and threat findings triage.

#### Storage & Multi-Tenancy
- Private cloud object storage (S3-compatible / Supabase Storage) with user-scoped object keys (`captures/{userId}/{fileId}.pcap`).
- Application-layer tenant isolation enforced on every read, write, and delete query in the service layer.
- Supabase PostgreSQL Row Level Security (RLS) policies providing defense-in-depth against direct database API access.
- Per-user storage quotas (default 500 MB cumulative capture storage, maximum 10 active files).

#### Observability, Operations & Disaster Recovery
- Prometheus metrics scraping endpoint (`/actuator/prometheus`) secured by `ROLE_ADMIN` authentication.
- Custom Micrometer instrumentation for inspection worker pool size, active threads, queue depth, and queue capacity.
- Spring Boot Actuator readiness and liveness health check groups (`/actuator/health/readiness`, `/actuator/health/liveness`).
- Automated database backup and restore scripts with strict destination host allowlisting (`restore-db.sh`, `restore-db.ps1`).
- Storage reconciliation utility (`scripts/dr/reconcile-storage.ts`) to detect orphaned objects or missing storage keys.
- Comprehensive Disaster Recovery Runbook (`RUNBOOK_DISASTER_RECOVERY.md`) covering database loss, storage corruption, and credentials rollback.

#### Beta Gate & Telemetry
- Configurable invite-only gate (`BETA_INVITE_ONLY`, `BETA_ALLOWED_EMAILS`) with case-insensitive and domain-level matching.
- User-friendly invite-only alert handling on registration and OAuth2 callback flows.
- Optional feedback mailto links (`VITE_FEEDBACK_EMAIL`) integrated into footer, sidebar, and error screens.
- Privacy-shielded crash reports stripping query parameters and hash fragments to prevent token leakage.

### Known Limitations & Post-Beta Roadmap
- **Offline Captures Only:** Operates exclusively on pre-captured PCAP and PCAPNG files. Does not support live network interface sniffing, SPAN/TAP mirroring, or real-time continuous sensor streams.
- **13 Deterministic Rules:** Threat detection is strictly deterministic based on protocol state and header parsing; no machine learning models or probabilistic anomaly detectors are active.
- **No Live SIEM Push Integrations:** Findings export via standard REST APIs and analyst reports; direct log shipping to Splunk, Chronicle, or Microsoft Sentinel is planned for post-beta.
- **No Multi-Factor Authentication (MFA):** Session security relies on strong passwords, email verification, and OAuth2 provider security; TOTP/WebAuthn is scheduled for Phase 2.
- **AI Copilot is Waitlist Preview:** The AI assistant workspace renders experimental preview metadata and waitlist enrollment; no live LLM model or external AI API is connected during the beta.
- **Initial Invite-Only Access:** System defaults to closed invite-only access at initial launch to validate infrastructure stability before opening self-service registration.
