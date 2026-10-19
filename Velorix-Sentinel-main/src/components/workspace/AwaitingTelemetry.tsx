import { RadioTower } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface AwaitingTelemetryProps {
  title?: string;
  detail?: string;
  icon?: LucideIcon;
  action?: ReactNode;
  compact?: boolean;
  className?: string;
}

/**
 * Canonical empty state for every SOC workspace surface. No alerts, analysts or
 * log lines are ever fabricated — panels stay honest until telemetry connects.
 */
export function AwaitingTelemetry({
  title = "No telemetry connected.",
  detail = "This panel populates from the SOC backend and realtime gateway. Nothing shown here is simulated.",
  icon: Icon = RadioTower,
  action,
  compact = false,
  className,
}: AwaitingTelemetryProps) {
  if (compact) {
    return (
      <div
        role="status"
        className={cn(
          "flex items-start gap-3 rounded-xl border border-dashed border-border/80 bg-card/30 px-3 py-2.5",
          className,
        )}
      >
        <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        <div className="min-w-0">
          <p className="text-xs font-medium">{title}</p>
          {detail && <p className="mt-0.5 text-[11px] text-muted-foreground">{detail}</p>}
        </div>
      </div>
    );
  }

  return (
    <div
      role="status"
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-border bg-card/20 px-6 py-10 text-center",
        className,
      )}
    >
      <span className="relative grid size-12 place-items-center rounded-xl bg-muted/60 text-muted-foreground">
        <span className="absolute inset-0 rounded-xl border border-primary/20 animate-[pulse_2.4s_cubic-bezier(0.4,0,0.6,1)_infinite]" />
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <h3 className="text-sm font-semibold">{title}</h3>
      {detail && <p className="max-w-md text-xs text-muted-foreground">{detail}</p>}
      {action}
    </div>
  );
}
