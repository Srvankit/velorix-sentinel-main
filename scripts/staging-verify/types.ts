/**
 * Shared types and data contracts for Velorix Sentinel Staging Verification Harness.
 * Task 8.2: Staging Golden PCAP Verification & Pre-flight Validation.
 */

export type StageName = "golden" | "large" | "quota" | "auth" | "abuse" | "persistence" | "all";
export type PersistencePhase = "before" | "after";

export type CheckStatus = "PASS" | "FAIL" | "SKIP";

export interface CheckFailureDetail {
  requestMethod?: string;
  requestUrl?: string;
  requestHeaders?: Record<string, string>;
  requestBodySnippet?: string;
  responseStatus?: number;
  responseBodySnippet?: string;
  errorMessage: string;
  reproductionSteps: string[];
}

export interface CheckResult {
  checkNumber: number;
  name: string;
  stage: StageName;
  status: CheckStatus;
  durationMs: number;
  sourceCitation: string;
  summary: string;
  failure?: CheckFailureDetail;
  metadata?: Record<string, unknown>;
}

export interface StageResult {
  stage: StageName;
  durationMs: number;
  passedCount: number;
  failedCount: number;
  skippedCount: number;
  checks: CheckResult[];
}

export interface HarnessRunSummary {
  timestamp: string;
  apiUrl: string;
  frontendUrl?: string;
  durationMs: number;
  totalChecks: number;
  passedCount: number;
  failedCount: number;
  skippedCount: number;
  success: boolean;
  stages: StageResult[];
}

export interface StagingConfig {
  apiUrl: string;
  frontendUrl: string;
  testEmail: string;
  testPassword: string;
  testEmail2?: string;
  testPassword2?: string;
  unverifiedEmail?: string;
  unverifiedPassword?: string;
  skipUi: boolean;
  timeoutMs: number;
}

export interface PersistenceState {
  fileId: string;
  analysisId: string;
  fileName: string;
  checksum: string;
  totalPackets: number;
  riskScore: number;
  totalThreats: number;
  timestamp: string;
}

export interface TokenSession {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  email: string;
}
