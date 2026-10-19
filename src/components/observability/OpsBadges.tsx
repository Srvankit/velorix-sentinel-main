import { CheckCircle2, CircleDashed, TriangleAlert, Wrench, XCircle } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import type { HealthState, LogSeverity, PipelineState } from "@/types/observability";

const HEALTH_TONE: Record<HealthState, { tone: string; icon: LucideIcon; label: string }> = {
  OPERATIONAL: {
    tone: "border-emerald-500/30 bg-emerald-500/10 text-emerald-400",
    icon: CheckCircle2,
    label: "Operational",
  },
  DEGRADED: {
    tone: "border-amber-500/30 bg-amber-500/10 text-amber-400",
    icon: TriangleAlert,
    label: "Degraded",
  },
  OUTAGE: {
    tone: "border-primary/40 bg-primary/10 text-primary",
    icon: XCircle,
    label: "Outage",
  },
  MAINTENANCE: {
    tone: "border-sky-500/30 bg-sky-500/10 text-sky-400",
    icon: Wrench,
    label: "Under Maintenance",
  },
  UNKNOWN: {
    tone: "border-border bg-muted/30 text-muted-foreground",
    icon: CircleDashed,
    label: "Awaiting Monitoring",
  },
};

export function HealthBadge({ state, className }: { state: HealthState; className?: string }) {
  const { tone, icon: Icon, label } = HEALTH_TONE[state];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-medium whitespace-nowrap",
        tone,
        className,
      )}
    >
      <Icon className="size-3" aria-hidden="true" />
      {label}
    </span>
  );
}

const SEVERITY_TONE: Record<LogSeverity, string> = {
  TRACE: "border-border bg-muted/30 text-muted-foreground",
  DEBUG: "border-slate-500/30 bg-slate-500/10 text-slate-300",
  INFO: "border-sky-500/30 bg-sky-500/10 text-sky-400",
  WARN: "border-amber-500/30 bg-amber-500/10 text-amber-400",
  ERROR: "border-primary/40 bg-primary/10 text-primary",
  FATAL: "border-primary/60 bg-primary/20 text-primary",
};

export function SeverityTag({ severity }: { severity: LogSeverity }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded border px-1.5 py-0.5 font-mono text-[10px] font-semibold tracking-wide",
        SEVERITY_TONE[severity],
      )}
    >
      {severity}
    </span>
  );
}

const PIPELINE_TONE: Record<PipelineState, { tone: string; label: string }> = {
  PASSING: { tone: "border-emerald-500/30 bg-emerald-500/10 text-emerald-400", label: "Passing" },
  FAILING: { tone: "border-primary/40 bg-primary/10 text-primary", label: "Failing" },
  RUNNING: { tone: "border-sky-500/30 bg-sky-500/10 text-sky-400", label: "Running" },
  NOT_CONFIGURED: {
    tone: "border-border bg-muted/30 text-muted-foreground",
    label: "Not Configured",
  },
};

export function PipelineBadge({ state }: { state: PipelineState }) {
  const { tone, label } = PIPELINE_TONE[state];
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border px-2.5 py-0.5 text-[10px] font-medium tracking-wide uppercase",
        tone,
      )}
    >
      {label}
    </span>
  );
}

export function ComingSoonTag({ label = "Coming Soon" }: { label?: string }) {
  return (
    <span className="inline-flex items-center rounded-full border border-border bg-muted/30 px-2.5 py-0.5 text-[10px] font-medium tracking-wide text-muted-foreground uppercase">
      {label}
    </span>
  );
}
