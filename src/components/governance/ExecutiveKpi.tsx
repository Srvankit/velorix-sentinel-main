import { Minus, TrendingDown, TrendingUp } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { motion } from "framer-motion";

import { Skeleton } from "@/components/common";
import { cn } from "@/lib/utils";
import type { PostureBand, TrendDirection } from "@/types/governance";

const BAND_TONE: Record<PostureBand, string> = {
  STRONG: "text-emerald-400 border-emerald-500/30 bg-emerald-500/10",
  MODERATE: "text-amber-400 border-amber-500/30 bg-amber-500/10",
  WEAK: "text-primary border-primary/30 bg-primary/10",
  UNKNOWN: "text-muted-foreground border-border bg-muted/30",
};

const BAND_LABEL: Record<PostureBand, string> = {
  STRONG: "Strong",
  MODERATE: "Moderate",
  WEAK: "Needs attention",
  UNKNOWN: "Unassessed",
};

interface ExecutiveKpiProps {
  label: string;
  icon: LucideIcon;
  value?: number | null;
  suffix?: string;
  band?: PostureBand;
  trend?: TrendDirection;
  delta?: number | null;
  hint?: string;
  isLoading?: boolean;
  delay?: number;
  className?: string;
}

/**
 * Premium executive KPI tile. Renders "Awaiting Live Data" whenever the metric
 * is null — the card never invents or interpolates a figure.
 */
export function ExecutiveKpi({
  label,
  icon: Icon,
  value = null,
  suffix = "",
  band = "UNKNOWN",
  trend = "UNKNOWN",
  delta = null,
  hint,
  isLoading = false,
  delay = 0,
  className,
}: ExecutiveKpiProps) {
  const hasValue = value !== null && value !== undefined;
  const TrendIcon = trend === "UP" ? TrendingUp : trend === "DOWN" ? TrendingDown : Minus;

  return (
    <motion.article
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1], delay }}
      className={cn(
        "group relative overflow-hidden rounded-2xl border border-border/70 bg-card/40 p-4 backdrop-blur-xl transition-colors hover:border-primary/30",
        className,
      )}
    >
      <div
        className="pointer-events-none absolute -top-16 -right-16 size-32 rounded-full bg-primary/10 opacity-0 blur-2xl transition-opacity group-hover:opacity-100"
        aria-hidden="true"
      />
      <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-3">
        <div className="min-w-0">
          <p className="truncate text-[11px] tracking-wide text-muted-foreground uppercase">
            {label}
          </p>
          {isLoading ? (
            <Skeleton className="mt-2 h-7 w-24" />
          ) : hasValue ? (
            <p className="mt-1 font-mono text-2xl leading-none font-semibold">
              {value}
              <span className="text-sm text-muted-foreground">{suffix}</span>
            </p>
          ) : (
            <p className="mt-1.5 text-xs font-medium text-muted-foreground">Awaiting Live Data</p>
          )}
        </div>
        <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
          <Icon className="size-4" aria-hidden="true" />
        </span>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <span
          className={cn(
            "inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium tracking-wide uppercase",
            BAND_TONE[band],
          )}
        >
          {BAND_LABEL[band]}
        </span>
        {hasValue && delta !== null && (
          <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground">
            <TrendIcon className="size-3" aria-hidden="true" />
            {delta > 0 ? "+" : ""}
            {delta}
          </span>
        )}
      </div>

      {hint && <p className="mt-2 text-[11px] text-muted-foreground">{hint}</p>}
    </motion.article>
  );
}
