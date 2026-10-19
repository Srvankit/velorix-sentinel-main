import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const statusBadge = cva(
  "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium",
  {
    variants: {
      tone: {
        neutral: "border-border bg-muted/50 text-muted-foreground",
        info: "border-info/40 bg-info/15 text-info",
        success: "border-success/40 bg-success/15 text-success",
        warning: "border-warning/40 bg-warning/15 text-warning",
        danger: "border-destructive/40 bg-destructive/15 text-destructive",
      },
    },
    defaultVariants: { tone: "neutral" },
  },
);

export type StatusTone = NonNullable<VariantProps<typeof statusBadge>["tone"]>;

/** Maps backend enum values onto a visual tone; unknown values stay neutral. */
const TONES: Record<string, StatusTone> = {
  // analysis / upload lifecycle
  UPLOADED: "neutral",
  VALIDATING: "info",
  READY_FOR_ANALYSIS: "info",
  QUEUED: "info",
  PROCESSING: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  DELETED: "neutral",
  // triage lifecycle
  OPEN: "danger",
  ACKNOWLEDGED: "warning",
  RESOLVED: "success",
  FALSE_POSITIVE: "neutral",
  // component health
  UP: "success",
  DEGRADED: "warning",
  DOWN: "danger",
};

interface StatusBadgeProps {
  status: string;
  tone?: StatusTone;
  className?: string;
}

/** Generic lifecycle pill shared by analyses, captures, findings and health widgets. */
export function StatusBadge({ status, tone, className }: StatusBadgeProps) {
  const resolved = tone ?? TONES[status] ?? "neutral";
  return (
    <span className={cn(statusBadge({ tone: resolved }), className)}>
      <span className="size-1.5 rounded-full bg-current" aria-hidden="true" />
      {status.toLowerCase().replace(/_/g, " ")}
    </span>
  );
}
