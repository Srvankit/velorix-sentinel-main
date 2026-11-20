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
- **Zero Critical/High Policy:** The project enforces zero critical and zero high-severity vulnerabilities across all dependency trees.
- **Current Baseline:** Live security audits confirm **0 Critical, 0 High**, 16 Moderate, and 0 Low vulnerabilities across 1,123 total dependencies.
- **Production vs Development Scope:** All 16 moderate findings reside exclusively within development tooling (`devDependencies`):
  - `@netlify/vite-plugin-tanstack-start` (build/deploy bundler) and its transitive utilities (`@netlify/zip-it-and-ship-it`, `@vercel/nft`, `archiver`, `glob`, `minimatch`) due to GHSA-g3hv-75wg-m249 (minimatch glob amplification) and GHSA-3g3m-2fjr-6pw4 (glob filesystem traversal timeout).
  - `typescript-eslint` (linter) and its sub-packages (`@typescript-eslint/parser`, `@typescript-eslint/typescript-estree`, `@typescript-eslint/eslint-plugin`) due to transitive `minimatch`.
  - The 381 production runtime dependencies contain **0 vulnerabilities**.
- **No Safe Automated Fix Available:** Upgrading these packages requires semver-major rewrites or downgrades (e.g. npm recommends downgrading `@netlify/vite-plugin-tanstack-start` to `1.1.0`), which would break the TanStack Start Vite build pipeline. These dev-only findings are actively tracked for upstream non-breaking patch releases.
- **Prohibition of `--force`:** The use of `npm audit fix --force` is strictly forbidden, as it introduces unverified breaking major-version bumps across foundational dependencies.
- **Transitive Remediation via Overrides:** When transitive dependencies have known vulnerabilities with upstream patches available, targeted resolutions must be applied via the `overrides` field in `package.json` (e.g., overriding `brace-expansion` to patched releases `^1.1.21 || ^5.0.12`).

### C. Framework & Package Specific Policies
1. **Nitro Dependency:**
   - *Status:* Pinned to `3.0.260603-beta` in `devDependencies`.
   - *Architecture Boundary:* In `vite.config.ts`, `nitro: false` is explicitly configured because Netlify handles the TanStack Start SSR/runtime via `@netlify/vite-plugin-tanstack-start`. The Nitro server engine is never invoked in development or production.
   - *Upstream State:* UnJS Nitro v3 has not published a GA non-beta release; the pinned version satisfies `@lovable.dev/vite-tanstack-config` optional peer requirements with zero known security advisories.
2. **Package Manager & Supply-Chain Release-Age Guard Distinction:**
   - *npm as Canonical Package Manager:* `npm` is the sole authoritative package manager for dependency resolution, package lifecycle scripts, and lockfile updates. `package-lock.json` (Lockfile Version 3) is the single source of truth.
   - *Scope of `bunfig.toml` Controls:* The `bunfig.toml` configuration (`minimumReleaseAge = 86400` seconds / 24 hours, `minimumReleaseAgeExcludes = []`) strictly governs Bun package manager commands (`bun install`, `bun add`). It does **not** automatically govern `npm` installations.
   - *npm Release-Age Support & CI Integrity:* Modern npm v11 (installed: `11.11.0`) provides a native `--min-release-age <days>` flag / `.npmrc` option (specified in whole integer days), which applies during dependency tree resolution (`npm install`). However, in production and CI/CD pipelines, supply-chain reproducibility is primarily guaranteed by `package-lock.json` via `npm ci`, which strictly enforces exact locked versions and cryptographic subresource integrity hashes (`sha512`).
   - *Zero-Bypass Policy:* All installed `@lovable.dev/*` packages were published months prior to release, fully satisfying publication age guards. Zero bypass exceptions (`minimumReleaseAgeExcludes = []`) are permitted.
3. **Test Runner Alignment:**
   - Frontend unit and integration tests are authored using the Bun test runner (`bun:test`) for execution speed, invoked uniformly via `npm test` (`"test": "bun test"` in `package.json`). Dependencies and types are managed by npm, with Bun executing tests against the npm-managed `node_modules`. Bun is retained exclusively as the test execution runtime, not as a package manager.

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
- **Plugin Configuration:** Integrated via `org.owasp:dependency-check-maven` (version `12.1.0`) in `backend/pom.xml`.
- **Isolated Execution Profile:** Configured under the dedicated `security-audit` profile (and `<pluginManagement>`) to generate both HTML and JSON reports under `backend/target/dependency-check-reports`.
- **Authoritative Command:**
  ```sh
  mvn dependency-check:check -Psecurity-audit -f backend/pom.xml
  # Or with an NVD API key:
  mvn dependency-check:check -Psecurity-audit -DnvdApiKey=<YOUR_NVD_API_KEY> -f backend/pom.xml
  ```
- **NVD API v2 Operational Behavior & Limitations:**
  - OWASP Dependency-Check synchronizes the National Vulnerability Database via the NVD REST API v2.
  - **Initial Cache Build:** On the initial run, the plugin downloads the complete NVD CVE corpus (~430,000 records) into a local H2 database cache (`~/.m2/repository/org/owasp/dependency-check-data/11.0/odc.mv.db`).
  - **Rate Limiting:** Without an NVD API key (`nvdApiKey`), the unauthenticated NVD API enforces a rolling limit of 5 requests per 30 seconds (~6 seconds between pagination chunks), requiring approximately 20–25 minutes for the initial database population.
  - **Incremental Updates:** Once the initial cache is built, subsequent executions only download incremental updates since the last sync timestamp, completing in seconds.
  - **Build Decoupling:** Regular test runs (`mvn test`) deliberately omit the `security-audit` profile so development testing and CI feedback loops remain instantaneous and resilient against network or rate-limiting failures.

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

# 2. Security dependency check (requires security-audit profile)
mvn dependency-check:check -Psecurity-audit -f backend/pom.xml
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

