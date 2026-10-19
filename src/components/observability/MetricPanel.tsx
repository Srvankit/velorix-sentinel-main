import { motion } from "framer-motion";

import { cn } from "@/lib/utils";
import type { MetricUnit, PlatformMetric } from "@/types/observability";

const UNIT_LABEL: Record<MetricUnit, string> = {
  PERCENT: "%",
  BYTES: "bytes",
  BYTES_PER_SEC: "B/s",
  REQ_PER_SEC: "req/s",
  MILLISECONDS: "ms",
  COUNT: "",
};

interface MetricPanelProps {
  metric: PlatformMetric;
  delay?: number;
  className?: string;
}

/**
 * Metric tile with a sparkline placeholder. Draws a real series when the
 * collector provides one; otherwise states that monitoring is not connected.
 */
export function MetricPanel({ metric, delay = 0, className }: MetricPanelProps) {
  const hasSeries = Array.isArray(metric.series) && metric.series.length > 1;
  const unit = UNIT_LABEL[metric.unit];

  const path = hasSeries ? buildSparkline(metric.series!.map((point) => point.value)) : null;

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
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-xs font-semibold">{metric.name}</h3>
          <p className="mt-0.5 text-[11px] text-muted-foreground">{metric.description}</p>
        </div>
        {metric.value != null ? (
          <p className="shrink-0 font-mono text-xl leading-none font-semibold">
            {metric.value}
            <span className="ml-0.5 text-xs text-muted-foreground">{unit}</span>
          </p>
        ) : null}
      </div>

      <div className="mt-3 h-16 rounded-xl border border-dashed border-border/70 bg-muted/10">
        {path ? (
          <svg viewBox="0 0 100 32" preserveAspectRatio="none" className="h-full w-full">
            <path
              d={path}
              fill="none"
              stroke="var(--color-primary)"
              strokeWidth="1.5"
              vectorEffect="non-scaling-stroke"
            />
          </svg>
        ) : (
          <div className="flex h-full items-center justify-center px-3 text-center">
            <p className="text-[11px] font-medium text-muted-foreground">Awaiting Live Metrics</p>
          </div>
        )}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border/60 pt-2.5 text-[10px] text-muted-foreground">
        <span>
          Warn:{" "}
          <span className="font-mono text-foreground/80">
            {metric.warnThreshold != null ? `${metric.warnThreshold}${unit}` : "—"}
          </span>
        </span>
        <span>
          Critical:{" "}
          <span className="font-mono text-foreground/80">
            {metric.critThreshold != null ? `${metric.critThreshold}${unit}` : "—"}
          </span>
        </span>
      </div>
    </motion.article>
  );
}

function buildSparkline(values: number[]): string {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  return values
    .map((value, index) => {
      const x = (index / (values.length - 1)) * 100;
      const y = 30 - ((value - min) / span) * 28;
      return `${index === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(" ");
}
