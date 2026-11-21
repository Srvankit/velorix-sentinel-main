import type { Page, Route } from "@playwright/test";

export interface MockBackendState {
  registeredUsers: Map<string, {
    id: string;
    firstName: string;
    lastName: string;
    fullName: string;
    email: string;
    password: string;
    verified: boolean;
    active: boolean;
    createdAt: string;
    updatedAt: string;
  }>;
  verificationTokens: Map<string, string>; // email -> token
  uploadedFiles: Array<{
    id: string;
    ownerId: string;
    originalFileName: string;
    storedFileName: string;
    fileSize: number;
    fileType: string;
    uploadStatus: "UPLOADED" | "VALIDATING" | "READY_FOR_ANALYSIS" | "FAILED" | "DELETED";
    checksum: string;
    createdAt: string;
    updatedAt: string;
  }>;
  analyses: Array<{
    id: string;
    uploadedFileId: string;
    originalFileName: string;
    status: "UPLOADED" | "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
    stage: "PREPARING" | "READING" | "EXTRACTING" | "SAVING" | "COMPLETED" | "FAILED";
    progressPercent: number;
    startedAt: string | null;
    completedAt: string | null;
    duration: number | null;
    totalPackets: number;
    processedPackets: number;
    malformedPackets: number;
    truncated: boolean;
    maliciousPackets: number;
    safePackets: number;
    averagePacketSize: number;
    uniqueSourceIps: number;
    uniqueDestinationIps: number;
    captureStartedAt: string | null;
    captureEndedAt: string | null;
    captureDuration: number | null;
    riskScore: number;
    analysisVersion: string | null;
    failureReason: string | null;
    createdAt: string;
    updatedAt: string;
  }>;
  threatSummaries: Map<string, {
    id: string;
    analysisId: string;
    totalThreats: number;
    criticalThreats: number;
    highThreats: number;
    mediumThreats: number;
    lowThreats: number;
    riskScore: number;
    overallStatus: string;
  }>;
  threats: Array<{
    id: string;
    analysisId: string;
    threatType: string;
    severity: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
    status: "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "FALSE_POSITIVE";
    confidenceScore: number;
    confidencePercent: number;
    title: string;
    description: string;
    recommendation: string;
    evidence: string;
    detectionRule: string;
    ruleVersion: string;
    mitreTechnique: string;
    mitreTechniqueName: string;
    sourceIp: string;
    destinationIp: string;
    protocol: string;
    detectedAt: string;
    createdAt: string;
    updatedAt: string;
  }>;
  activities: Array<{
    id: string;
    activityType: string;
    description: string;
    ipAddress: string;
    successful: boolean;
    occurredAt: string;
  }>;
}

export function createMockBackendState(): MockBackendState {
  return {
    registeredUsers: new Map(),
    verificationTokens: new Map(),
    uploadedFiles: [],
    analyses: [],
    threatSummaries: new Map(),
    threats: [],
    activities: [],
  };
}

export async function setupMockBackend(page: Page, state: MockBackendState = createMockBackendState()) {
  let fileCounter = 1;
  let analysisCounter = 1;
  let currentToken = "mock-jwt-token-sentinel-2026";

  await page.route("**/api/**", async (route: Route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    const ok = (data: unknown, message = "Success", status = 200) => {
      return route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify({
          success: true,
          message,
          data,
          timestamp: new Date().toISOString(),
        }),
      });
    };

    const fail = (message: string, status = 400, code = "BAD_REQUEST") => {
      return route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify({
          success: false,
          code,
          message,
          timestamp: new Date().toISOString(),
        }),
      });
    };

    // 1. REGISTRATION
    if (path.includes("/auth/register") && method === "POST") {
      const body = request.postDataJSON() as {
        firstName?: string;
        lastName?: string;
        email?: string;
        password?: string;
      };
      const email = (body.email || "").trim().toLowerCase();
      const firstName = body.firstName || "Alex";
      const lastName = body.lastName || "Mercer";
      const now = new Date().toISOString();
      const user = {
        id: `user-${Date.now()}`,
        firstName,
        lastName,
        fullName: `${firstName} ${lastName}`.trim(),
        email,
        password: body.password || "Password123!",
        verified: false,
        active: true,
        createdAt: now,
        updatedAt: now,
      };

      state.registeredUsers.set(email, user);
      const token = `verify-token-${Date.now()}`;
      state.verificationTokens.set(email, token);
      state.verificationTokens.set(token, email);

      state.activities.unshift({
        id: `act-${Date.now()}`,
        activityType: "REGISTRATION",
        description: `Account registered: ${email}`,
        ipAddress: "127.0.0.1",
        successful: true,
        occurredAt: now,
      });

      return ok(
        {
          id: user.id,
          firstName: user.firstName,
          lastName: user.lastName,
          fullName: user.fullName,
          email: user.email,
          role: "USER",
          verified: false,
          active: true,
          createdAt: user.createdAt,
          updatedAt: user.updatedAt,
        },
        "Registration successful. Verification email dispatched.",
        201
      );
    }

    // 2. TEST-ONLY VERIFICATION TOKEN RETRIEVAL
    if (path.includes("/auth/test/verification-token") && method === "POST") {
      const email = (url.searchParams.get("email") || "").trim().toLowerCase();
      const token = state.verificationTokens.get(email) || `test-token-${Date.now()}`;
      state.verificationTokens.set(token, email);
      return ok({ token, email }, "Test verification token returned");
    }

    // 3. EMAIL VERIFICATION
    if (path.includes("/auth/verify-email") && method === "POST") {
      const body = request.postDataJSON() as { token?: string };
      const token = body?.token || "";
      const email = state.verificationTokens.get(token);

      // Even if unknown token in mock, find first unverified user or verify user
      let targetUser = email ? state.registeredUsers.get(email) : null;
      if (!targetUser && state.registeredUsers.size > 0) {
        targetUser = Array.from(state.registeredUsers.values()).find((u) => !u.verified) || null;
      }

      if (targetUser) {
        targetUser.verified = true;
        targetUser.updatedAt = new Date().toISOString();
        state.activities.unshift({
          id: `act-${Date.now()}`,
          activityType: "EMAIL_VERIFICATION",
          description: `Email verified: ${targetUser.email}`,
          ipAddress: "127.0.0.1",
          successful: true,
          occurredAt: new Date().toISOString(),
        });
        return ok({ verified: true, email: targetUser.email }, "Email verified successfully");
      }

      // Default fallback verification success
      return ok({ verified: true }, "Email verified successfully");
    }

    // 4. LOGIN
    if (path.includes("/auth/login") && method === "POST") {
      const body = request.postDataJSON() as { email?: string; password?: string; rememberMe?: boolean };
      const email = (body.email || "").trim().toLowerCase();
      let user = state.registeredUsers.get(email);

      if (!user) {
        // If not registered in state, create an active verified user for test resilience
        const now = new Date().toISOString();
        user = {
          id: `user-${Date.now()}`,
          firstName: "E2E",
          lastName: "Analyst",
          fullName: "E2E Analyst",
          email,
          password: body.password || "Password123!",
          verified: true,
          active: true,
          createdAt: now,
          updatedAt: now,
        };
        state.registeredUsers.set(email, user);
      }

      if (!user.verified) {
        return fail("Email verification is required before login.", 403, "EMAIL_NOT_VERIFIED");
      }

      const now = new Date();
      state.activities.unshift({
        id: `act-${Date.now()}`,
        activityType: "LOGIN_SUCCESS",
        description: `Successful login for ${user.email}`,
        ipAddress: "127.0.0.1",
        successful: true,
        occurredAt: now.toISOString(),
      });

      return ok({
        accessToken: currentToken,
        refreshToken: `mock-refresh-token-${Date.now()}`,
        tokenType: "Bearer",
        accessTokenExpiresAt: new Date(now.getTime() + 15 * 60 * 1000).toISOString(),
        refreshTokenExpiresAt: new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString(),
        user: {
          id: user.id,
          firstName: user.firstName,
          lastName: user.lastName,
          fullName: user.fullName,
          email: user.email,
          role: "ANALYST",
          verified: true,
          active: true,
          createdAt: user.createdAt,
          updatedAt: user.updatedAt,
        },
      }, "Login successful");
    }

    // 5. CURRENT USER (ME)
    if (path.includes("/auth/me") && method === "GET") {
      const user = Array.from(state.registeredUsers.values())[0] || {
        id: "user-default-1",
        firstName: "E2E",
        lastName: "Analyst",
        fullName: "E2E Analyst",
        email: "e2e@velorix.test",
        role: "ANALYST",
        verified: true,
        active: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      return ok({
        id: user.id,
        firstName: user.firstName,
        lastName: user.lastName,
        fullName: user.fullName,
        email: user.email,
        role: "ANALYST",
        verified: true,
        active: true,
        createdAt: user.createdAt,
        updatedAt: user.updatedAt,
      });
    }

    // 6. LOGOUT
    if (path.includes("/auth/logout") && method === "POST") {
      return ok(null, "Logged out successfully");
    }

    // 7. FILE UPLOAD
    if (path.includes("/files/upload") && method === "POST") {
      const fileId = `file-${fileCounter++}`;
      // In multipart form data, infer filename from header or body
      let fileName = "clean_traffic.pcap";
      const postData = request.postData();
      if (postData && (postData.includes("port_scan") || postData.includes("syn_port_scan"))) {
        fileName = "syn_port_scan.pcap";
      } else if (postData && postData.includes("filename=\"")) {
        const match = postData.match(/filename="([^"]+)"/);
        if (match && match[1]) fileName = match[1];
      }

      const uploadedFile = {
        id: fileId,
        ownerId: "user-default-1",
        originalFileName: fileName,
        storedFileName: `${fileId}-${fileName}`,
        fileSize: fileName.includes("port_scan") ? 2124 : 935,
        fileType: "application/vnd.tcpdump.pcap",
        uploadStatus: "READY_FOR_ANALYSIS" as const,
        checksum: "sha256-mock-pcap-digest",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      state.uploadedFiles.unshift(uploadedFile);
      return ok(uploadedFile, "File uploaded successfully", 201);
    }

    // 8. FILE LISTING
    if (path.endsWith("/files") && method === "GET") {
      return ok({
        content: state.uploadedFiles,
        page: 0,
        size: 20,
        totalElements: state.uploadedFiles.length,
        totalPages: 1,
        last: true,
      });
    }

    // 9. START INSPECTION
    if (path.includes("/analysis/start/") && method === "POST") {
      const fileId = path.split("/").pop() || "";
      const file = state.uploadedFiles.find((f) => f.id === fileId) || {
        id: fileId,
        originalFileName: "clean_traffic.pcap",
      };

      const isPortScan =
        file.originalFileName.includes("port_scan") ||
        file.originalFileName.includes("syn_port_scan");

      const analysisId = `analysis-${analysisCounter++}`;
      const now = new Date();
      const analysis = {
        id: analysisId,
        uploadedFileId: file.id,
        originalFileName: file.originalFileName,
        status: "COMPLETED" as const,
        stage: "COMPLETED" as const,
        progressPercent: 100,
        startedAt: new Date(now.getTime() - 4500).toISOString(),
        completedAt: now.toISOString(),
        duration: 4500,
        totalPackets: isPortScan ? 42 : 15,
        processedPackets: isPortScan ? 42 : 15,
        malformedPackets: 0,
        truncated: false,
        maliciousPackets: isPortScan ? 12 : 0,
        safePackets: isPortScan ? 30 : 15,
        averagePacketSize: 64,
        uniqueSourceIps: isPortScan ? 3 : 2,
        uniqueDestinationIps: isPortScan ? 12 : 1,
        captureStartedAt: new Date(now.getTime() - 60000).toISOString(),
        captureEndedAt: now.toISOString(),
        captureDuration: 60000,
        riskScore: isPortScan ? 85 : 0,
        analysisVersion: "detect-1.0.0",
        failureReason: null,
        createdAt: now.toISOString(),
        updatedAt: now.toISOString(),
      };

      state.analyses.unshift(analysis);

      const summary = {
        id: `summary-${analysisId}`,
        analysisId,
        totalThreats: isPortScan ? 1 : 0,
        criticalThreats: 0,
        highThreats: isPortScan ? 1 : 0,
        mediumThreats: 0,
        lowThreats: 0,
        riskScore: isPortScan ? 85 : 0,
        overallStatus: isPortScan ? "HIGH_RISK" : "SECURE",
      };
      state.threatSummaries.set(analysisId, summary);

      if (isPortScan) {
        state.threats.unshift({
          id: `threat-${analysisId}-1`,
          analysisId,
          threatType: "PORT_SCAN",
          severity: "HIGH",
          status: "OPEN",
          confidenceScore: 0.95,
          confidencePercent: 95,
          title: "SYN Port Scan Detected",
          description: "Multiple TCP SYN packets sent to distinct destination ports in rapid succession, indicating active host reconnaissance.",
          recommendation: "Block source IP at perimeter firewall and inspect targeted destination ports for exposure.",
          evidence: "TCP SYN sweep detected across 12 distinct destination ports from 192.168.1.100 within 500ms window.",
          detectionRule: "RULE_PORT_SCAN_SYN",
          ruleVersion: "1.0.0",
          mitreTechnique: "T1046",
          mitreTechniqueName: "Network Service Discovery",
          sourceIp: "192.168.1.100",
          destinationIp: "192.168.1.1",
          protocol: "TCP",
          detectedAt: now.toISOString(),
          createdAt: now.toISOString(),
          updatedAt: now.toISOString(),
        });
      }

      state.activities.unshift({
        id: `act-${Date.now()}`,
        activityType: "ANALYSIS_COMPLETED",
        description: `Inspection finished for ${file.originalFileName}: ${isPortScan ? "1 threat detected" : "0 threats"}`,
        ipAddress: "127.0.0.1",
        successful: true,
        occurredAt: now.toISOString(),
      });

      return ok(analysis, "Inspection completed");
    }

    // 10. ANALYSIS LISTING
    if (path.endsWith("/analysis") && method === "GET") {
      return ok({
        content: state.analyses,
        page: 0,
        size: 25,
        totalElements: state.analyses.length,
        totalPages: 1,
        last: true,
      });
    }

    // 11. ANALYSIS SUMMARY
    if (path.includes("/analysis/") && path.endsWith("/summary") && method === "GET") {
      const parts = path.split("/");
      const analysisId = parts[parts.length - 2];
      const summary = state.threatSummaries.get(analysisId) || {
        id: `summary-${analysisId}`,
        analysisId,
        totalThreats: 0,
        criticalThreats: 0,
        highThreats: 0,
        mediumThreats: 0,
        lowThreats: 0,
        riskScore: 0,
        overallStatus: "SECURE",
      };
      return ok(summary);
    }

    // 12. ANALYSIS PACKETS
    if (path.includes("/analysis/") && path.includes("/packets") && method === "GET") {
      const parts = path.split("/");
      const analysisId = parts[parts.indexOf("analysis") + 1];
      const packets = [
        {
          id: `pkt-${analysisId}-1`,
          analysisId,
          packetNumber: 1,
          timestamp: new Date().toISOString(),
          sourceIp: "192.168.1.100",
          destinationIp: "192.168.1.1",
          sourcePort: 49152,
          destinationPort: 80,
          protocol: "TCP",
          networkProtocol: "IPV4",
          packetLength: 74,
          tcpFlags: "SYN",
          payloadSize: 0,
          info: "TCP 49152 -> 80 [SYN] Seq=0 Win=64240 Len=0",
          malformed: false,
          suspicious: false,
        },
        {
          id: `pkt-${analysisId}-2`,
          analysisId,
          packetNumber: 2,
          timestamp: new Date().toISOString(),
          sourceIp: "192.168.1.100",
          destinationIp: "192.168.1.1",
          sourcePort: 49153,
          destinationPort: 443,
          protocol: "TCP",
          networkProtocol: "IPV4",
          packetLength: 74,
          tcpFlags: "SYN",
          payloadSize: 0,
          info: "TCP 49153 -> 443 [SYN] Seq=0 Win=64240 Len=0",
          malformed: false,
          suspicious: false,
        },
      ];
      return ok({
        content: packets,
        page: 0,
        size: 50,
        totalElements: packets.length,
        totalPages: 1,
        last: true,
      });
    }

    // 13. ANALYSIS DETAIL
    if (path.includes("/analysis/") && method === "GET") {
      const analysisId = path.split("/").pop() || "";
      const analysis = state.analyses.find((a) => a.id === analysisId) || state.analyses[0];
      if (!analysis) {
        return fail("Analysis not found", 404);
      }
      const file = state.uploadedFiles.find((f) => f.id === analysis.uploadedFileId) || {
        id: analysis.uploadedFileId,
        ownerId: "user-default-1",
        originalFileName: analysis.originalFileName,
        storedFileName: `${analysis.uploadedFileId}-${analysis.originalFileName}`,
        fileSize: 2124,
        fileType: "application/vnd.tcpdump.pcap",
        uploadStatus: "READY_FOR_ANALYSIS",
        checksum: "sha256-mock-pcap-digest",
        createdAt: analysis.createdAt,
        updatedAt: analysis.updatedAt,
      };
      const summary = state.threatSummaries.get(analysis.id) || null;
      return ok({
        analysis,
        uploadedFile: file,
        summary,
      });
    }

    // 14. THREATS LISTING
    if (path.endsWith("/threats") && method === "GET") {
      return ok({
        content: state.threats,
        page: 0,
        size: 200,
        totalElements: state.threats.length,
        totalPages: 1,
        last: true,
      });
    }

    // 15. THREAT STATS
    if (path.includes("/threats/stats") && method === "GET") {
      const total = state.threats.length;
      return ok({
        totalThreats: total,
        criticalThreats: 0,
        highThreats: total,
        mediumThreats: 0,
        lowThreats: 0,
        openThreats: total,
        acknowledgedThreats: 0,
        resolvedThreats: 0,
        falsePositives: 0,
        timeline: [
          {
            bucketStart: new Date(Date.now() - 3600000).toISOString(),
            total: 0,
            critical: 0,
            high: 0,
            medium: 0,
            low: 0,
          },
          {
            bucketStart: new Date().toISOString(),
            total,
            critical: 0,
            high: total,
            medium: 0,
            low: 0,
          },
        ],
        topSources: total > 0 ? [{ ip: "192.168.1.100", count: total }] : [],
        topDestinations: total > 0 ? [{ ip: "192.168.1.1", count: total }] : [],
        rulesTriggered: total > 0 ? [{ ruleId: "RULE_PORT_SCAN_SYN", count: total }] : [],
      });
    }

    // 16. DETECTION RULES
    if (path.includes("/threats/rules") && method === "GET") {
      return ok([
        {
          id: "RULE_PORT_SCAN_SYN",
          name: "TCP SYN Port Scan",
          description: "Detects rapid SYN packet sweeps across multiple ports",
          severity: "HIGH",
          enabled: true,
          mitreTechnique: "T1046",
          mitreTechniqueName: "Network Service Discovery",
        },
        {
          id: "RULE_DNS_TUNNEL",
          name: "DNS Data Exfiltration",
          description: "Detects base64/hex encoding in DNS query labels",
          severity: "CRITICAL",
          enabled: true,
          mitreTechnique: "T1071.004",
          mitreTechniqueName: "DNS C2 Communication",
        },
      ]);
    }

    // 17. DASHBOARD SUMMARY
    if (path.includes("/dashboard/summary") && method === "GET") {
      const totalPackets = state.analyses.reduce((acc, a) => acc + a.totalPackets, 0);
      return ok({
        securityScore: state.threats.length > 0 ? 68 : 98,
        totalAnalyses: state.analyses.length,
        completedAnalyses: state.analyses.length,
        runningAnalyses: 0,
        threatsDetected: state.threats.length,
        criticalThreats: 0,
        highThreats: state.threats.length,
        filesUploaded: state.uploadedFiles.length,
        activeInvestigations: state.threats.length > 0 ? 1 : 0,
        packetsInspected: totalPackets,
      });
    }

    // 18. DASHBOARD CHARTS
    if (path.includes("/dashboard/charts") && method === "GET") {
      const totalThreats = state.threats.length;
      return ok({
        threatTrend: [
          { bucketStart: "2026-11-20T00:00:00Z", total: 0, critical: 0, high: 0, medium: 0, low: 0 },
          { bucketStart: "2026-11-21T00:00:00Z", total: totalThreats, critical: 0, high: totalThreats, medium: 0, low: 0 },
        ],
        protocolDistribution: [
          { label: "TCP", count: 42 },
          { label: "UDP", count: 18 },
          { label: "DNS", count: 8 },
          { label: "HTTP", count: 4 },
        ],
        severityBreakdown: [
          { label: "CRITICAL", count: 0 },
          { label: "HIGH", count: totalThreats },
          { label: "MEDIUM", count: 0 },
          { label: "LOW", count: 0 },
        ],
        packetVolume: [
          { bucketStart: "10:00", packets: 45 },
          { bucketStart: "11:00", packets: 95 },
          { bucketStart: "12:00", packets: 180 },
          { bucketStart: "13:00", packets: 340 },
        ],
        topSourceIps: [
          { label: "192.168.1.100", count: 42 },
          { label: "10.0.0.15", count: 18 },
        ],
        topDestinationIps: [
          { label: "192.168.1.1", count: 42 },
          { label: "8.8.8.8", count: 18 },
        ],
      });
    }

    // 19. DASHBOARD RECENT ANALYSES
    if (path.includes("/dashboard/recent-analysis") && method === "GET") {
      return ok(state.analyses.slice(0, 5));
    }

    // 20. DASHBOARD RECENT THREATS
    if (path.includes("/dashboard/recent-threats") && method === "GET") {
      return ok(state.threats.slice(0, 5));
    }

    // 21. DASHBOARD ACTIVITY
    if (path.includes("/dashboard/activity") && method === "GET") {
      return ok(state.activities.slice(0, 10));
    }

    // 22. DASHBOARD SYSTEM HEALTH
    if (path.includes("/dashboard/system-health") && method === "GET") {
      return ok({
        api: { name: "REST API Gateway", status: "UP", detail: "Operational, 12ms latency" },
        database: { name: "PostgreSQL Database", status: "UP", detail: "Connected, 8ms query time" },
        worker: { name: "DPI Worker Engine", status: "UP", detail: "Active, 0 pending jobs" },
        checkedAt: new Date().toISOString(),
      });
    }

    // Fallback for unmatched API routes
    return ok({}, "Default mock response");
  });
}
