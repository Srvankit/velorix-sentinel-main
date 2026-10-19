import type { LucideIcon } from "lucide-react";

import { Skeleton } from "@/components/common/Skeletons";
import { cn } from "@/lib/utils";

interface DashboardCardProps {
  label: string;
  value: string;
  hint?: string;
  icon: LucideIcon;
  tone?: "default" | "danger" | "warning" | "success";
  isLoading?: boolean;
  className?: string;
}

const TONE_CLASSES = {
  default: "bg-primary/10 text-primary",
  danger: "bg-destructive/15 text-destructive",
  warning: "bg-warning/15 text-warning",
  success: "bg-success/15 text-success",
} as const;

/** Single KPI tile. Values always come from the API — never computed client-side. */
export function DashboardCard({
  label,
  value,
  hint,
  icon: Icon,
  tone = "default",
  isLoading = false,
  className,
}: DashboardCardProps) {
  return (
    <article className={cn("glass-panel rounded-2xl p-5", className)}>
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">{label}</p>
        <span className={cn("grid size-9 place-items-center rounded-lg", TONE_CLASSES[tone])}>
          <Icon className="size-4" aria-hidden="true" />
        </span>
      </div>
      {isLoading ? (
        <Skeleton className="mt-4 h-8 w-24" />
      ) : (
        <p className="mt-3 font-display text-3xl font-semibold tracking-tight tabular-nums">
          {value}
        </p>
      )}
      {hint && !isLoading && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
    </article>
  );
}
