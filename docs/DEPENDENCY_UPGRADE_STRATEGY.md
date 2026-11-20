# Velorix Sentinel — Dependency Upgrade & Maintenance Strategy

**Author:** Velorix Technologies  
**Scope:** Velorix Sentinel Frontend (TanStack Start / React) & Backend (Spring Boot / Java 21)  
**Task Reference:** Task 6.6 (M13) — Dependency Maintenance, Lockfile Unification & Pinning  

---

## 1. Overview & Objectives

Velorix Sentinel enforces a disciplined, reproducible, and secure dependency management lifecycle across both frontend and backend repositories. The core objectives are:
1. **Deterministic Builds:** Guarantee that every build environment (local developer workstations, CI runners, container images, and Netlify/Render deployment platforms) resolves identical dependency graphs.
2. **Supply-Chain Integrity:** Enforce release-age guards, eliminate unauthorized vendor bypasses, and prevent vulnerable or compromised packages from entering production artifacts.
3. **Continuous Vulnerability Remediation:** Proactively audit direct and transitive dependencies against authoritative CVE databases (`npm audit`, GitHub Advisories, and OWASP Dependency-Check / NVD).
4. **Safe, Predictable Upgrades:** Maintain alignment with framework maintenance patch lines without introducing unexpected major-version breaking changes.

---

## 2. Frontend Dependency Strategy

### A. Authoritative Package Manager: npm
- **Standard Tooling:** The frontend dependency management is standardized exclusively on **npm** with `package-lock.json` (Lockfile Version 3).
- **Single Lockfile Rule:** Dual lockfiles (such as coexisting `bun.lock` and `package-lock.json`) are strictly prohibited to prevent environment drift. `package-lock.json` is the sole committed lockfile.
- **Reproducible Installs:** CI pipelines and production deployment commands (e.g., Netlify build scripts) must always execute `npm ci` rather than `npm install`. `npm ci` guarantees that `node_modules` strictly reflects the committed `package-lock.json` without updating versions or re-resolving ranges.

### B. Security Audits & Vulnerability Remediation
- **Continuous Audit:** Every dependency modification must be verified with `npm audit`.
- **Zero Critical/High Policy:** The project enforces zero critical and zero high-severity vulnerabilities in the dependency tree.
- **Prohibition of `--force`:** The use of `npm audit fix --force` is strictly forbidden, as it introduces unverified breaking major-version bumps across foundational dependencies.
- **Transitive Remediation via Overrides:** When transitive dependencies have known vulnerabilities with upstream patches available, targeted resolutions must be applied via the `overrides` field in `package.json` (e.g., overriding `brace-expansion` to patched releases `^1.1.21 || ^5.0.12`).

### C. Framework & Package Specific Policies
1. **Nitro Dependency:**
   - *Status:* Pinned to `3.0.260603-beta` in `devDependencies`.
   - *Architecture Boundary:* In `vite.config.ts`, `nitro: false` is explicitly configured because Netlify handles the TanStack Start SSR/runtime via `@netlify/vite-plugin-tanstack-start`. The Nitro server engine is never invoked in development or production.
   - *Upstream State:* UnJS Nitro v3 has not published a GA non-beta release; the pinned version satisfies `@lovable.dev/vite-tanstack-config` optional peer requirements with zero known security advisories.
2. **Lovable Packages & Supply-Chain Release-Age Guard:**
   - *24-Hour Guard:* To defend against immediate supply-chain attacks and malicious zero-day package takeovers, release-age controls (`minimumReleaseAge = 86400`) are maintained.
   - *Zero Bypass Policy:* In `bunfig.toml`, `minimumReleaseAgeExcludes` is strictly set to `[]`. No package bypasses the 24-hour publication window. All installed `@lovable.dev/*` packages were published months prior, satisfying the safety window without requiring exceptions.
3. **Test Runner Alignment:**
   - Frontend unit and integration tests are authored using the high-performance Bun test runner (`bun:test`), invoked via `npm test` (`"test": "bun test"` in `package.json`). Dependencies are managed and resolved by npm, with Bun executing test files against the npm-managed `node_modules`.

---

## 3. Backend Dependency Strategy

### A. Maven Version Management & BOM Inheritance
- **Parent BOM:** The backend inherits from `org.springframework.boot:spring-boot-starter-parent`.
- **Spring Boot Version Policy:**
  - Upgrades within the active minor line (Spring Boot 3.5.x) are evaluated continuously for maintenance patches (e.g., upgrading `3.5.0` to `3.5.16`).
  - Patch updates deliver essential security patches, Hibernate updates, Spring Security fixes, and HikariCP stability improvements without breaking backward compatibility.
  - Major-version migrations (e.g., Spring Boot 3.x to 4.x) must follow an explicit RFC and architectural review plan.
- **Ecosystem Alignment:** When upgrading Spring Boot parent versions, third-party libraries that bind to Spring MVC or Servlet internals must be kept in lockstep. Specifically:
  - `springdoc-openapi-starter-webmvc-ui` must be updated alongside Spring Boot (e.g., `2.8.17` for Spring Boot 3.5.x) to prevent reflection or OpenAPI schema generation regressions.

### B. Deep Packet Inspection & Pcap4J Policy
- **Library Version:** Pcap4J is pinned to `1.8.2` (`pcap4j-core` and `pcap4j-packetfactory-static`).
- **Maintenance Assessment:** Version 1.8.2 is the final stable GA release. Upstream development has ceased active feature work, and 2.0.0 never advanced beyond alpha status.
- **Architectural Boundary:**
  - Velorix Sentinel isolates Pcap4J exclusively to **pure Java packet decoding** (`org.pcap4j.packet.*`).
  - No native C/C++ libpcap binaries (`libpcap.so` or `wpcap.dll`) are loaded or required at runtime; all PCAP/PCAPNG captures are parsed directly from byte streams or storage files.
  - Retaining 1.8.2 avoids untested alpha decoders while preserving 100% deterministic packet dissection across the 13 detection engine rules. Future replacement requires a dedicated migration plan to a modern pure-Java PCAP parser.

### C. OWASP Dependency-Check Integration
- **Plugin Configuration:** Integrated via `org.owasp:dependency-check-maven` in `backend/pom.xml`.
- **Isolated Execution Profile:** Configured under the `security-audit` profile (and `pluginManagement`) to generate both HTML and JSON reports under `target/dependency-check-reports`.
- **Command:**
  ```sh
  mvn dependency-check:check -f backend/pom.xml
  # Or with full verification:
  mvn verify -Psecurity-audit -f backend/pom.xml
  ```
- **NVD API v2 Considerations:**
  - OWASP Dependency-Check queries the National Vulnerability Database via NVD API v2.
  - Without an NVD API key (`nvdApiKey`), NVD enforces severe rate limits (~6-second delay between pagination requests).
  - Normal test execution (`mvn test`) deliberately excludes the dependency check plugin so daily builds and offline testing remain fast, reliable, and unblocked.
  - In CI/CD environments, supply `NVD_API_KEY` to enable rapid automated vulnerability verification.

---

## 4. Regression Testing & Verification Requirements

Prior to committing any dependency change (frontend or backend), the following test matrix must pass with zero regressions:

### Frontend Test Verification
```sh
# 1. Clean installation from lockfile
npm ci

# 2. Security audit check (must have 0 critical and 0 high findings)
npm audit

# 3. Full test suite execution (all 126 unit/integration tests must pass)
npm test

# 4. Production build compilation
npm run build
```

### Backend Test Verification
```sh
# 1. Full regression test suite execution (398+ tests must pass)
mvn test -f backend/pom.xml

# 2. Security dependency check
mvn dependency-check:check -f backend/pom.xml
```

---

## 5. Rollback & Version Recovery Strategy

If an unexpected regression or incompatibility is discovered following a dependency update:
1. **Never Rewrite Git History:** Never force-push, squash, or amend published history on shared branches.
2. **Clean Git Revert:** Use standard Git commits to revert the specific dependency bump:
   ```sh
   git revert <commit-hash>
   ```
3. **Lockfile Synchronization:** When reverting `package.json` or `pom.xml`, verify that `package-lock.json` or Maven dependencies are re-synchronized immediately so lockfiles never drift.
4. **Post-Revert Validation:** Re-run the full regression test suite (`npm test`, `npm run build`, `mvn test`) to confirm stability before redeployment.

---

## 6. Review Cadence & Maintenance Schedule

- **Monthly Maintenance:** Run `npm audit` and `mvn versions:display-dependency-updates` to identify patch releases for active libraries.
- **Framework Lifecycles:** Track Spring Boot quarterly maintenance releases and upgrade within the active 3.5.x line.
- **Zero Suppression Policy:** Vulnerability suppressions in OWASP Dependency-Check or npm audit must be narrowly scoped, temporary, and documented with verifiable justification. Blanket suppressions are strictly prohibited.

