import { cn } from "@/lib/utils";
import { riskBand } from "./risk-band";

interface RiskBadgeProps {
  /** Backend risk score between 0 and 100. */
  score: number;
  className?: string;
  showLabel?: boolean;
}

/** Compact 0-100 risk indicator used in tables and detail headers. */
export function RiskBadge({ score, className, showLabel = true }: RiskBadgeProps) {
  const clamped = Math.max(0, Math.min(100, Math.round(score)));
  const band = riskBand(clamped);
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-mono text-xs font-medium",
        band.className,
        className,
      )}
      title={`Risk score ${clamped} of 100`}
    >
      {clamped}
      {showLabel && <span className="font-sans">{band.label}</span>}
    </span>
  );
}
