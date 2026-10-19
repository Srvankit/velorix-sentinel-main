import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

interface IntelMetricProps {
  label: string;
  value: string | number | null;
  hint?: string;
  icon?: LucideIcon;
  className?: string;
}

/** KPI tile. A null value renders an explicit awaiting marker, never a zero. */
export function IntelMetric({ label, value, hint, icon: Icon, className }: IntelMetricProps) {
  const pending = value === null || value === undefined;

  return (
    <div
      className={cn(
        "rounded-xl border border-border/70 bg-card/40 p-4 transition-colors hover:border-primary/30",
        className,
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium text-muted-foreground">{label}</p>
        {Icon && <Icon className="size-4 text-primary/80" aria-hidden="true" />}
      </div>
      <p
        className={cn(
          "mt-2 font-semibold tracking-tight",
          pending ? "text-sm text-muted-foreground" : "text-2xl",
        )}
      >
        {pending ? "Awaiting feed" : value}
      </p>
      {hint && <p className="mt-1 text-[11px] text-muted-foreground">{hint}</p>}
    </div>
  );
}
