# Security & Vulnerability Disclosure Policy

Velorix Sentinel takes the security of our packet inspection engine, network threat detection platform, and user data seriously. We appreciate the efforts of independent security researchers who practice responsible disclosure.

---

## 1. Reporting a Vulnerability

If you discover a security vulnerability in Velorix Sentinel, please report it privately:

- **Security Contact:** `security@example.com`  
  <!-- TODO (OWNER): Replace security@example.com with your real, monitored security contact email address before public launch -->
- **Subject Line:** `[Vulnerability Report] Velorix Sentinel - <Component/Vulnerability Type>`
- **Content:**
  - Detailed description of the vulnerability.
  - Step-by-step reproduction instructions or proof-of-concept (PoC).
  - Affected components, endpoints, or dependency versions.
  - An assessment of the potential impact.

Please **do not** open public GitHub issues, discussions, or pull requests for suspected security vulnerabilities.

---

## 2. Scope

### In Scope
- Web application interface (`https://<app-domain>`)
- Backend REST API endpoints (`https://<api-domain>/api/*`)
- Authentication, session lifecycle, OAuth2 integration, and token handling
- Deep packet inspection (PCAP/PCAPNG) parser integrity and engine isolation
- Multi-tenant data segregation and object storage access controls
- Rate-limiting mechanisms and account security features

### Out of Scope
- Denial of Service (DoS/DDoS) attacks against hosting or upstream providers (Render, Netlify, Supabase, Cloudflare)
- Social engineering, phishing, or physical attacks against employees or contractors
- Reports of missing security headers that do not lead to demonstrable vulnerabilities
- Vulnerabilities affecting outdated client browsers or unsupported platforms
- Automated scanner reports without a verifiable proof of concept

---

## 3. Response Expectations & SLAs

Velorix Sentinel is operated by a small engineering team. We commit to the following reasonable targets:

- **Initial Acknowledgment:** We will make every effort to acknowledge receipt of your report within **5 business days**.
- **Triage & Assessment:** We aim to assess severity and validity within **10 business days** of acknowledgment.
- **Resolution & Disclosure:** We will work to remediate valid issues in a prioritized timeline based on severity and provide updates as fixes are deployed. We request that you maintain confidentiality until a patch is released.

---

## 4. Safe Harbor

If you conduct vulnerability research in good faith and in compliance with this policy:

- We will consider your research to be authorized.
- We will not initiate legal action or contact law enforcement regarding your security research activities.
- You must refrain from accessing, modifying, or deleting other users' private packet captures, account credentials, or telemetry.
- If you inadvertently encounter another tenant's data, stop testing immediately, purge local copies, and notify us in your report.
