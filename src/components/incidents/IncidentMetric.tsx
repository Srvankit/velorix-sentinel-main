import type { LucideIcon } from "lucide-react";
import { motion } from "framer-motion";

import { cn } from "@/lib/utils";

interface IncidentMetricProps {
  label: string;
  value: string | number | null;
  hint?: string;
  icon?: LucideIcon;
  tone?: "default" | "critical" | "positive";
  delay?: number;
  className?: string;
}

const TONE: Record<NonNullable<IncidentMetricProps["tone"]>, string> = {
  default: "text-primary/80",
  critical: "text-primary",
  positive: "text-emerald-400",
};

/**
 * Executive KPI tile. A null value renders an explicit awaiting marker rather
 * than a zero, so an unconnected backend is never mistaken for "all clear".
 */
export function IncidentMetric({
  label,
  value,
  hint,
  icon: Icon,
  tone = "default",
  delay = 0,
  className,
}: IncidentMetricProps) {
  const pending = value === null || value === undefined;

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1], delay }}
      className={cn(
        "glass-panel group relative overflow-hidden p-4 transition-colors hover:border-primary/30",
        className,
      )}
    >
      <span
        className="pointer-events-none absolute -top-12 -right-12 size-28 rounded-full bg-primary/5 opacity-0 blur-2xl transition-opacity duration-500 group-hover:opacity-100"
        aria-hidden="true"
      />
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium text-muted-foreground">{label}</p>
        {Icon && <Icon className={cn("size-4", TONE[tone])} aria-hidden="true" />}
      </div>
      <p
        className={cn(
          "mt-2 font-semibold tracking-tight",
          pending ? "text-sm text-muted-foreground" : "text-2xl",
        )}
      >
        {pending ? "Awaiting backend" : value}
      </p>
      {hint && <p className="mt-1 text-[11px] text-muted-foreground">{hint}</p>}
    </motion.div>
  );
}
