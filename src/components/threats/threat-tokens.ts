import type { ThreatSeverity, ThreatStatus, OverallStatus } from "@/types/threats";

/** Presentation tokens for the detection module — colours stay semantic. */

export const SEVERITY_TEXT: Record<ThreatSeverity, string> = {
  CRITICAL: "text-critical",
  HIGH: "text-destructive",
  MEDIUM: "text-warning",
  LOW: "text-info",
};

export const SEVERITY_SURFACE: Record<ThreatSeverity, string> = {
  CRITICAL: "border-critical/40 bg-critical/10",
  HIGH: "border-destructive/40 bg-destructive/10",
  MEDIUM: "border-warning/40 bg-warning/10",
  LOW: "border-info/40 bg-info/10",
};

export const STATUS_CLASS: Record<ThreatStatus, string> = {
  OPEN: "border-destructive/40 bg-destructive/10 text-destructive",
  ACKNOWLEDGED: "border-warning/40 bg-warning/10 text-warning",
  RESOLVED: "border-success/40 bg-success/10 text-success",
  FALSE_POSITIVE: "border-border bg-muted text-muted-foreground",
};

/** Healthy / Warning / Critical posture derived from the backend verdict. */
export function postureFromStatus(status: OverallStatus | null | undefined): {
  label: string;
  className: string;
} {
  switch (status) {
    case "SECURE":
    case "LOW_RISK":
      return { label: "Healthy", className: "text-success" };
    case "ELEVATED":
      return { label: "Warning", className: "text-warning" };
    case "HIGH_RISK":
    case "CRITICAL":
      return { label: "Critical", className: "text-critical" };
    default:
      return { label: "Awaiting analysis", className: "text-muted-foreground" };
  }
}

/** Guards every numeric read so NaN / Infinity / null never reach the UI. */
export function safeCount(value: number | null | undefined): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

/** Returns null when the backend has not produced a usable value yet. */
export function safeScore(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function textOrDash(value: string | null | undefined): string {
  return value && value.trim().length > 0 ? value : "—";
}
