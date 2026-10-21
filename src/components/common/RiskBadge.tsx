import { cn } from "@/lib/utils";
import { riskBand, type RiskBandOptions } from "./risk-band";

interface RiskBadgeProps extends RiskBandOptions {
  /** Backend risk score between 0 and 100. */
  score: number;
  className?: string;
  showLabel?: boolean;
}

/** Compact 0-100 risk indicator used in tables and detail headers. */
export function RiskBadge({
  score,
  status,
  criticalThreats,
  highThreats,
  overallStatus,
  isDetectionComplete,
  className,
  showLabel = true,
}: RiskBadgeProps) {
  const clamped = Math.max(0, Math.min(100, Math.round(score)));
  const band = riskBand(clamped, {
    status,
    criticalThreats,
    highThreats,
    overallStatus,
    isDetectionComplete,
  });

  const title = band.isScanned
    ? `Risk score ${clamped} of 100 (${band.label})`
    : `Risk status: ${band.label}`;

  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-mono text-xs font-medium",
        band.className,
        className,
      )}
      title={title}
    >
      {band.isScanned ? clamped : "—"}
      {showLabel && <span className="font-sans">{band.label}</span>}
    </span>
  );
}
