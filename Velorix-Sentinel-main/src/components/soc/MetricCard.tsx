import type { LucideIcon } from "lucide-react";
import { motion } from "framer-motion";

import { Skeleton } from "@/components/common/Skeletons";
import { cn } from "@/lib/utils";

export type MetricTone = "default" | "danger" | "warning" | "success" | "info";

export interface MetricCardProps {
  label: string;
  /** Numeric/string value. Pass `undefined` together with `awaiting` for pending metrics. */
  value?: string | number;
  hint?: string;
  icon: LucideIcon;
  tone?: MetricTone;
  isLoading?: boolean;
  /** Renders the standard "Awaiting Data" treatment instead of a fake number. */
  awaiting?: boolean;
  delay?: number;
  className?: string;
}

const TONE_ICON: Record<MetricTone, string> = {
  default: "bg-primary/10 text-primary ring-primary/20",
  danger: "bg-destructive/15 text-destructive ring-destructive/20",
  warning: "bg-warning/15 text-warning ring-warning/20",
  success: "bg-success/15 text-success ring-success/20",
  info: "bg-info/15 text-info ring-info/20",
};

const TONE_GLOW: Record<MetricTone, string> = {
  default: "from-primary/12",
  danger: "from-destructive/14",
  warning: "from-warning/14",
  success: "from-success/14",
  info: "from-info/14",
};

/**
 * Premium SOC KPI tile. Values are always API-sourced — metrics without a
 * backing endpoint render as "Awaiting Data" rather than fabricated numbers.
 */
export function MetricCard({
  label,
  value,
  hint,
  icon: Icon,
  tone = "default",
  isLoading = false,
  awaiting = false,
  delay = 0,
  className,
}: MetricCardProps) {
  return (
    <motion.article
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1], delay }}
      className={cn(
        "group glass-panel relative isolate overflow-hidden rounded-2xl p-5",
        "transition-all duration-300 hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lg hover:shadow-primary/5",
        className,
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          "pointer-events-none absolute -top-16 -right-16 -z-10 size-40 rounded-full bg-gradient-to-br to-transparent opacity-0 blur-2xl transition-opacity duration-500 group-hover:opacity-100",
          TONE_GLOW[tone],
        )}
      />

      <div className="flex items-start justify-between gap-3">
        <p className="min-w-0 text-xs font-medium tracking-wide text-muted-foreground uppercase">
          {label}
        </p>
        <span
          className={cn(
            "grid size-9 shrink-0 place-items-center rounded-lg ring-1 transition-transform duration-300 group-hover:scale-105",
            TONE_ICON[tone],
          )}
        >
          <Icon className="size-4" aria-hidden="true" />
        </span>
      </div>

      {isLoading ? (
        <Skeleton className="mt-4 h-8 w-24" />
      ) : awaiting ? (
        <p className="mt-3 inline-flex items-center gap-2 rounded-md border border-dashed border-border bg-muted/30 px-2 py-1 text-xs font-medium text-muted-foreground">
          <span className="size-1.5 animate-pulse rounded-full bg-muted-foreground/60" />
          Awaiting Data
        </p>
      ) : (
        <p className="mt-3 font-display text-3xl font-semibold tracking-tight tabular-nums">
          {value ?? "—"}
        </p>
      )}

      {hint && !isLoading && (
        <p className="mt-1 truncate text-xs text-muted-foreground" title={hint}>
          {hint}
        </p>
      )}
    </motion.article>
  );
}
