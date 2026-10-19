import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface StatRowProps {
  label: string;
  value?: ReactNode;
  hint?: string;
  className?: string;
}

/**
 * Label/value line used across the observability panels. When no value is
 * supplied it states that live monitoring is not connected.
 */
export function StatRow({ label, value, hint, className }: StatRowProps) {
  const empty = value === null || value === undefined || value === "";

  return (
    <div
      className={cn(
        "flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5",
        className,
      )}
    >
      <div className="min-w-0">
        <p className="truncate text-xs font-medium">{label}</p>
        {hint && <p className="mt-0.5 text-[11px] text-muted-foreground">{hint}</p>}
      </div>
      {empty ? (
        <span className="text-[11px] text-muted-foreground">Awaiting Live Monitoring</span>
      ) : (
        <span className="font-mono text-xs">{value}</span>
      )}
    </div>
  );
}
