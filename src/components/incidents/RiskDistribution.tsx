import { AwaitingBackend } from "./AwaitingBackend";
import { cn } from "@/lib/utils";
import type { IncidentSeverity } from "@/types/incident";

const ORDER: IncidentSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFORMATIONAL"];

const BAR_TONE: Record<IncidentSeverity, string> = {
  CRITICAL: "bg-primary",
  HIGH: "bg-orange-500",
  MEDIUM: "bg-amber-500",
  LOW: "bg-sky-500",
  INFORMATIONAL: "bg-muted-foreground/50",
};

interface RiskDistributionProps {
  distribution: Record<IncidentSeverity, number> | null;
  className?: string;
}

/** Severity breakdown bar chart. Null distribution renders an honest empty state. */
export function RiskDistribution({ distribution, className }: RiskDistributionProps) {
  if (!distribution) {
    return (
      <AwaitingBackend
        compact
        title="Risk distribution available after backend integration."
        className={className}
      />
    );
  }

  const total = ORDER.reduce((sum, key) => sum + (distribution[key] ?? 0), 0);

  if (total === 0) {
    return (
      <AwaitingBackend compact title="No incidents have been detected." className={className} />
    );
  }

  return (
    <div className={cn("space-y-3", className)}>
      {ORDER.map((severity) => {
        const count = distribution[severity] ?? 0;
        const percent = Math.round((count / total) * 100);
        return (
          <div key={severity} className="space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <span className="font-medium text-foreground">
                {severity.charAt(0) + severity.slice(1).toLowerCase()}
              </span>
              <span className="text-muted-foreground">
                {count} · {percent}%
              </span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-muted/40">
              <div
                className={cn(
                  "h-full rounded-full transition-all duration-700",
                  BAR_TONE[severity],
                )}
                style={{ width: `${percent}%` }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
