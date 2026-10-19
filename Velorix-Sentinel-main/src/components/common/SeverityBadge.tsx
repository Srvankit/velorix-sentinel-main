import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";
import type { Severity } from "@/types/threat";

const severityBadge = cva(
  "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium tracking-wide uppercase",
  {
    variants: {
      severity: {
        CRITICAL: "border-critical/40 bg-critical/15 text-critical",
        HIGH: "border-destructive/40 bg-destructive/15 text-destructive",
        MEDIUM: "border-warning/40 bg-warning/15 text-warning",
        LOW: "border-success/40 bg-success/15 text-success",
        INFO: "border-info/40 bg-info/15 text-info",
      },
    },
    defaultVariants: { severity: "INFO" },
  },
);

interface SeverityBadgeProps extends VariantProps<typeof severityBadge> {
  severity: Severity;
  className?: string;
}

/** Severity pill shared by threat tables, detail views and reports. */
export function SeverityBadge({ severity, className }: SeverityBadgeProps) {
  return (
    <span className={cn(severityBadge({ severity }), className)}>
      <span className="size-1.5 rounded-full bg-current" aria-hidden="true" />
      {severity.toLowerCase()}
    </span>
  );
}
