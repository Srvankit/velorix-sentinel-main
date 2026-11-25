/**
 * HTTP API client for Velorix Sentinel Staging Verification.
 * Implements authentication session management, multipart upload, inspection polling,
 * and error capturing with token redaction.
 */

import fs from "node:fs";
import path from "node:path";
import type { TokenSession } from "./types.ts";

export interface ApiResponseEnvelope<T> {
  success: boolean;
  message?: string;
  data: T;
  errors?: Array<{ code: string; message: string; field?: string }>;
  timestamp: string;
}

export interface UploadedFileDto {
  id: string;
  originalFileName: string;
  storedFileName: string;
  fileSize: number;
  uploadStatus: string;
  checksum: string;
  createdAt: string;
}

export interface AnalysisDto {
  id: string;
  uploadedFileId: string;
  originalFileName: string;
  status: "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
  stage?: string;
  progressPercent: number;
  duration?: number;
  totalPackets: number;
  processedPackets: number;
  malformedPackets: number;
  truncated: boolean;
  maliciousPackets: number;
  safePackets: number;
  riskScore: number;
  failureReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AnalysisSummaryDto {
  id: string;
  analysisId: string;
  totalThreats: number;
  criticalThreats: number;
  highThreats: number;
  mediumThreats: number;
  lowThreats: number;
  riskScore: number;
  overallStatus: "SECURE" | "LOW_RISK" | "ELEVATED" | "HIGH_RISK" | "CRITICAL";
}

export interface ThreatDto {
  id: string;
  analysisId: string;
  threatType: string;
  severity: string;
  status: string;
  confidenceScore: number;
  title: string;
  description: string;
  detectionRule: string;
  sourceIp?: string;
  destinationIp?: string;
}

export interface PacketDto {
  id: string;
  analysisId: string;
  packetNumber: number;
  sourceIp: string;
  destinationIp: string;
  protocol: string;
  malformed: boolean;
  suspicious: boolean;
}

export interface RawHttpResponse {
  status: number;
  headers: Headers;
  bodyText: string;
  json?: unknown;
}

export class StagingApiClient {
  private session: TokenSession | null = null;

  constructor(public readonly baseUrl: string) {}

  public getSession(): TokenSession | null {
    return this.session;
  }

  public setSession(session: TokenSession | null) {
    this.session = session;
  }

  public redact(text: string): string {
    if (!text) return "";
    return text
      .replace(/("password"\s*:\s*)"[^"]+"/gi, '$1"***REDACTED***"')
      .replace(/("accessToken"\s*:\s*)"[^"]+"/gi, '$1"***REDACTED***"')
      .replace(/("refreshToken"\s*:\s*)"[^"]+"/gi, '$1"***REDACTED***"')
      .replace(/(Bearer\s+)[A-Za-z0-9\-._~+/]+=*/g, "$1***REDACTED***");
  }

  private authHeaders(): Record<string, string> {
    if (this.session?.accessToken) {
      return { Authorization: `Bearer ${this.session.accessToken}` };
    }
    return {};
  }

  public async rawRequest(endpoint: string, options: RequestInit = {}): Promise<RawHttpResponse> {
    const url = `${this.baseUrl}${endpoint.startsWith("/") ? endpoint : `/${endpoint}`}`;
    const headers = new Headers(options.headers || {});

    // Inject auth if not explicitly overridden
    if (!headers.has("Authorization") && this.session?.accessToken) {
      headers.set("Authorization", `Bearer ${this.session.accessToken}`);
    }

    const response = await fetch(url, { ...options, headers });
    const bodyText = await response.text();
    let json: unknown;
    try {
      json = JSON.parse(bodyText);
    } catch {
      // Non-json response
    }

    return {
      status: response.status,
      headers: response.headers,
      bodyText,
      json,
    };
  }

  public async login(email: string, pass: string): Promise<RawHttpResponse> {
    const res = await this.rawRequest("/api/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password: pass }),
    });

    if (res.status === 200 && res.json) {
      const envelope = res.json as ApiResponseEnvelope<{
        accessToken: string;
        refreshToken: string;
        tokenType: string;
        expiresIn: number;
      }>;
      if (envelope.data?.accessToken) {
        this.session = {
          accessToken: envelope.data.accessToken,
          refreshToken: envelope.data.refreshToken,
          tokenType: envelope.data.tokenType || "Bearer",
          expiresIn: envelope.data.expiresIn || 900,
          email,
        };
      }
    }

    return res;
  }

  public async refresh(refreshToken?: string): Promise<RawHttpResponse> {
    const tokenToUse = refreshToken || this.session?.refreshToken;
    const res = await this.rawRequest("/api/v1/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: tokenToUse }),
    });

    if (res.status === 200 && res.json) {
      const envelope = res.json as ApiResponseEnvelope<{
        accessToken: string;
        refreshToken: string;
        tokenType: string;
        expiresIn: number;
      }>;
      if (envelope.data?.accessToken && this.session) {
        this.session.accessToken = envelope.data.accessToken;
        this.session.refreshToken = envelope.data.refreshToken;
      }
    }

    return res;
  }

  public async uploadCapture(filePath: string, customName?: string): Promise<RawHttpResponse> {
    const fileBytes = await fs.promises.readFile(filePath);
    const fileName = customName || path.basename(filePath);

    const formData = new FormData();
    const blob = new Blob([fileBytes], { type: "application/octet-stream" });
    formData.append("file", blob, fileName);

    return this.rawRequest("/api/v1/files/upload", {
      method: "POST",
      body: formData,
    });
  }

  public async startInspection(fileId: string): Promise<RawHttpResponse> {
    return this.rawRequest(`/api/v1/analysis/start/${fileId}`, {
      method: "POST",
    });
  }

  public async getAnalysis(analysisId: string): Promise<RawHttpResponse> {
    return this.rawRequest(`/api/v1/analysis/${analysisId}`, {
      method: "GET",
    });
  }

  public async getAnalysisSummary(analysisId: string): Promise<RawHttpResponse> {
    return this.rawRequest(`/api/v1/analysis/${analysisId}/summary`, {
      method: "GET",
    });
  }

  public async listThreats(analysisId: string): Promise<RawHttpResponse> {
    return this.rawRequest(`/api/v1/threats?analysisId=${analysisId}`, {
      method: "GET",
    });
  }

  public async listPackets(analysisId: string, page = 0, size = 50): Promise<RawHttpResponse> {
    return this.rawRequest(`/api/v1/analysis/${analysisId}/packets?page=${page}&size=${size}`, {
      method: "GET",
    });
  }

  public async listCaptures(): Promise<RawHttpResponse> {
    return this.rawRequest("/api/v1/files", {
      method: "GET",
    });
  }

  public async getCapture(fileId: string): Promise<RawHttpResponse> {
    return this.rawRequest(`/api/v1/files/${fileId}`, {
      method: "GET",
    });
  }

  public async deleteCapture(fileId: string): Promise<RawHttpResponse> {
    return this.rawRequest(`/api/v1/files/${fileId}`, {
      method: "DELETE",
    });
  }

  public async reRunDetection(analysisId: string): Promise<RawHttpResponse> {
    return this.rawRequest(`/api/v1/threats/run/${analysisId}`, {
      method: "POST",
    });
  }

  public async getActuatorHealth(): Promise<RawHttpResponse> {
    return this.rawRequest("/actuator/health", {
      method: "GET",
    });
  }

  /**
   * Polls an analysis run until it enters COMPLETED or FAILED state, with timeout and backoff.
   */
  public async pollAnalysisUntilComplete(
    analysisId: string,
    maxWaitMs = 60000,
    pollIntervalMs = 2000,
  ): Promise<{ analysis: AnalysisDto; summary?: AnalysisSummaryDto; elapsedMs: number }> {
    const started = Date.now();
    let delay = pollIntervalMs;

    while (Date.now() - started < maxWaitMs) {
      const res = await this.getAnalysis(analysisId);
      if (res.status === 200 && res.json) {
        const envelope = res.json as ApiResponseEnvelope<{
          analysis: AnalysisDto;
          summary?: AnalysisSummaryDto;
        }>;
        const analysis = envelope.data?.analysis;
        if (analysis) {
          if (analysis.status === "COMPLETED" || analysis.status === "FAILED") {
            return {
              analysis,
              summary: envelope.data.summary,
              elapsedMs: Date.now() - started,
            };
          }
        }
      }

      await new Promise((r) => setTimeout(r, delay));
      delay = Math.min(delay * 1.2, 5000);
    }

    throw new Error(
      `Analysis ${analysisId} timed out after ${maxWaitMs}ms before entering terminal status`,
    );
  }
}
