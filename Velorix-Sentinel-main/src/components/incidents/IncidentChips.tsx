import { cn } from "@/lib/utils";
import { humaniseEnum as humanise } from "@/utils/format";
import type {
  IncidentCategory,
  IncidentPriority,
  IncidentSeverity,
  IncidentStatus,
} from "@/types/incident";

const SEVERITY_TONE: Record<IncidentSeverity, string> = {
  CRITICAL: "border-primary/50 bg-primary/15 text-primary",
  HIGH: "border-orange-500/40 bg-orange-500/10 text-orange-400",
  MEDIUM: "border-amber-500/40 bg-amber-500/10 text-amber-400",
  LOW: "border-sky-500/40 bg-sky-500/10 text-sky-400",
  INFORMATIONAL: "border-border bg-muted/40 text-muted-foreground",
};

const STATUS_TONE: Record<IncidentStatus, string> = {
  NEW: "border-primary/40 bg-primary/10 text-primary",
  TRIAGED: "border-sky-500/40 bg-sky-500/10 text-sky-400",
  INVESTIGATING: "border-amber-500/40 bg-amber-500/10 text-amber-400",
  CONTAINED: "border-violet-500/40 bg-violet-500/10 text-violet-300",
  ERADICATED: "border-teal-500/40 bg-teal-500/10 text-teal-300",
  RECOVERED: "border-emerald-500/40 bg-emerald-500/10 text-emerald-400",
  RESOLVED: "border-emerald-500/40 bg-emerald-500/10 text-emerald-400",
  CLOSED: "border-border bg-muted/40 text-muted-foreground",
  FALSE_POSITIVE: "border-border bg-muted/40 text-muted-foreground",
};

const PRIORITY_TONE: Record<IncidentPriority, string> = {
  P1: "border-primary/50 bg-primary/15 text-primary",
  P2: "border-orange-500/40 bg-orange-500/10 text-orange-400",
  P3: "border-amber-500/40 bg-amber-500/10 text-amber-400",
  P4: "border-border bg-muted/40 text-muted-foreground",
};

const BASE =
  "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-medium whitespace-nowrap";

export function SeverityChip({
  severity,
  className,
}: {
  severity: IncidentSeverity;
  className?: string;
}) {
  return (
    <span className={cn(BASE, SEVERITY_TONE[severity], className)}>
      <span className="size-1.5 rounded-full bg-current" aria-hidden="true" />
      {humanise(severity)}
    </span>
  );
}

export function IncidentStatusChip({
  status,
  className,
}: {
  status: IncidentStatus;
  className?: string;
}) {
  return <span className={cn(BASE, STATUS_TONE[status], className)}>{humanise(status)}</span>;
}

export function PriorityChip({
  priority,
  className,
}: {
  priority: IncidentPriority;
  className?: string;
}) {
  return <span className={cn(BASE, PRIORITY_TONE[priority], className)}>{priority}</span>;
}

export function CategoryChip({
  category,
  className,
}: {
  category: IncidentCategory;
  className?: string;
}) {
  return (
    <span className={cn(BASE, "border-border bg-muted/40 text-muted-foreground", className)}>
      {humanise(category)}
    </span>
  );
}
