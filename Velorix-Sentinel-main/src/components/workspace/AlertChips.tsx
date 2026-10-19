import { cn } from "@/lib/utils";
import { humaniseEnum as humanise } from "@/utils/format";
import type {
  AlertCategory,
  AlertPriority,
  AlertSeverity,
  AlertStatus,
  ComponentState,
} from "@/types/soc";

const SEVERITY: Record<AlertSeverity, string> = {
  CRITICAL: "border-primary/50 bg-primary/15 text-primary",
  HIGH: "border-orange-500/40 bg-orange-500/10 text-orange-400",
  MEDIUM: "border-amber-500/40 bg-amber-500/10 text-amber-400",
  LOW: "border-sky-500/40 bg-sky-500/10 text-sky-400",
  INFORMATIONAL: "border-border bg-muted/50 text-muted-foreground",
};

const STATUS: Record<AlertStatus, string> = {
  NEW: "border-primary/40 bg-primary/10 text-primary",
  TRIAGED: "border-sky-500/40 bg-sky-500/10 text-sky-400",
  IN_PROGRESS: "border-amber-500/40 bg-amber-500/10 text-amber-400",
  ESCALATED: "border-orange-500/40 bg-orange-500/10 text-orange-400",
  SUPPRESSED: "border-border bg-muted/50 text-muted-foreground",
  CLOSED: "border-emerald-500/40 bg-emerald-500/10 text-emerald-400",
  FALSE_POSITIVE: "border-border bg-muted/50 text-muted-foreground",
};

const PRIORITY: Record<AlertPriority, string> = {
  P1: "border-primary/50 bg-primary/15 text-primary",
  P2: "border-orange-500/40 bg-orange-500/10 text-orange-400",
  P3: "border-amber-500/40 bg-amber-500/10 text-amber-400",
  P4: "border-border bg-muted/50 text-muted-foreground",
};

const COMPONENT_STATE: Record<ComponentState, { className: string; label: string }> = {
  OPERATIONAL: { className: "text-emerald-400", label: "Operational" },
  DEGRADED: { className: "text-amber-400", label: "Degraded" },
  OFFLINE: { className: "text-primary", label: "Offline" },
  UNKNOWN: { className: "text-muted-foreground", label: "Unknown" },
};

const BASE =
  "inline-flex shrink-0 items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-medium tracking-wide uppercase";

export function AlertSeverityChip({ severity }: { severity: AlertSeverity }) {
  return <span className={cn(BASE, SEVERITY[severity])}>{humanise(severity)}</span>;
}

export function AlertStatusChip({ status }: { status: AlertStatus }) {
  return <span className={cn(BASE, STATUS[status])}>{humanise(status)}</span>;
}

export function AlertPriorityChip({ priority }: { priority: AlertPriority }) {
  return <span className={cn(BASE, PRIORITY[priority])}>{priority}</span>;
}

export function AlertCategoryChip({ category }: { category: AlertCategory }) {
  return (
    <span className={cn(BASE, "border-border bg-muted/40 text-muted-foreground")}>
      {humanise(category)}
    </span>
  );
}

/** Animated indicator for a platform component's live state. */
export function StatusPulse({ state, className }: { state: ComponentState; className?: string }) {
  const meta = COMPONENT_STATE[state];
  const animate = state === "OPERATIONAL" || state === "DEGRADED";
  return (
    <span className={cn("inline-flex items-center gap-2 text-[11px]", meta.className, className)}>
      <span className="relative grid size-2.5 place-items-center">
        {animate && (
          <span className="absolute inset-0 rounded-full bg-current opacity-40 animate-ping" />
        )}
        <span className="size-1.5 rounded-full bg-current" />
      </span>
      {meta.label}
    </span>
  );
}

/** Risk score pill, coloured by band. */
export function RiskScore({ score }: { score: number | null }) {
  if (score === null || score === undefined) {
    return <span className="font-mono text-xs text-muted-foreground">—</span>;
  }
  const tone =
    score >= 80
      ? "text-primary"
      : score >= 60
        ? "text-orange-400"
        : score >= 40
          ? "text-amber-400"
          : "text-emerald-400";
  return <span className={cn("font-mono text-xs font-semibold", tone)}>{score}</span>;
}
