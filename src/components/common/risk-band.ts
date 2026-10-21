import type { AnalysisStatus } from "@/types/analysis";

export type RiskClassification =
  | "CRITICAL"
  | "HIGH_RISK"
  | "ELEVATED"
  | "LOW_RISK"
  | "SECURE"
  | "AWAITING_SCAN"
  | "PROCESSING"
  | "FAILED";

export interface RiskBandResult {
  classification: RiskClassification;
  label: string;
  className: string;
  isScanned: boolean;
}

export interface RiskBandOptions {
  status?: AnalysisStatus | string | null;
  criticalThreats?: number | null;
  highThreats?: number | null;
  overallStatus?: string | null;
  isDetectionComplete?: boolean | null;
}

/**
 * Risk bands synchronized with the backend `RiskScorer` and `OverallStatus` thresholds.
 *
 * Rules:
 * - CRITICAL: score >= 80 OR critical threats > 0 OR overallStatus === "CRITICAL"
 * - HIGH: score >= 50 OR high threats > 0 OR overallStatus === "HIGH_RISK" (unless CRITICAL applies)
 * - ELEVATED: score >= 25 OR overallStatus === "ELEVATED" (unless higher applies)
 * - LOW: score > 0 OR overallStatus === "LOW_RISK" (unless higher applies)
 * - SECURE: score == 0 AND completed with successful detection pass
 * - Otherwise (QUEUED / PROCESSING / FAILED / incomplete): explicit non-secure scanning state
 */
export function riskBand(score: number, options?: RiskBandOptions): RiskBandResult {
  const status = options?.status?.toUpperCase();
  const criticalThreats = options?.criticalThreats ?? 0;
  const highThreats = options?.highThreats ?? 0;
  const isDetectionComplete = options?.isDetectionComplete;

  // 1. Explicit non-completed analysis lifecycle states
  if (status === "FAILED") {
    return {
      classification: "FAILED",
      label: "Failed",
      className: "border-destructive/40 bg-destructive/15 text-destructive",
      isScanned: false,
    };
  }

  if (status === "PROCESSING") {
    return {
      classification: "PROCESSING",
      label: "Scanning…",
      className: "border-warning/40 bg-warning/15 text-warning",
      isScanned: false,
    };
  }

  if (status === "QUEUED" || status === "UPLOADED") {
    return {
      classification: "AWAITING_SCAN",
      label: "Awaiting scan",
      className: "border-info/40 bg-info/15 text-info",
      isScanned: false,
    };
  }

  // 2. Explicitly flagged as detection not yet completed
  if (isDetectionComplete === false) {
    return {
      classification: "AWAITING_SCAN",
      label: "Awaiting scan",
      className: "border-info/40 bg-info/15 text-info",
      isScanned: false,
    };
  }

  // 3. Completed or evaluated score
  const clamped = Math.max(0, Math.min(100, Math.round(score)));

  if (clamped >= 80 || criticalThreats > 0 || options?.overallStatus === "CRITICAL") {
    return {
      classification: "CRITICAL",
      label: "Critical",
      className: "border-critical/40 bg-critical/15 text-critical",
      isScanned: true,
    };
  }

  if (clamped >= 50 || highThreats > 0 || options?.overallStatus === "HIGH_RISK") {
    return {
      classification: "HIGH_RISK",
      label: "High",
      className: "border-destructive/40 bg-destructive/15 text-destructive",
      isScanned: true,
    };
  }

  if (clamped >= 25 || options?.overallStatus === "ELEVATED") {
    return {
      classification: "ELEVATED",
      label: "Elevated",
      className: "border-warning/40 bg-warning/15 text-warning",
      isScanned: true,
    };
  }

  if (clamped > 0 || options?.overallStatus === "LOW_RISK") {
    return {
      classification: "LOW_RISK",
      label: "Low",
      className: "border-info/40 bg-info/15 text-info",
      isScanned: true,
    };
  }

  return {
    classification: "SECURE",
    label: "Secure",
    className: "border-success/40 bg-success/15 text-success",
    isScanned: true,
  };
}
