import { DatabaseZap } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface AwaitingAdminDataProps {
  title?: string;
  detail?: string;
  icon?: LucideIcon;
  action?: ReactNode;
  compact?: boolean;
  className?: string;
}

/**
 * Canonical honest placeholder for every administration surface. No tenant,
 * user, session or audit record is ever fabricated.
 */
export function AwaitingAdminData({
  title = "Awaiting Live Data",
  detail = "This section populates from the administration backend. Nothing shown here is simulated.",
  icon: Icon = DatabaseZap,
  action,
  compact = false,
  className,
}: AwaitingAdminDataProps) {
  if (compact) {
    return (
      <div
        role="status"
        className={cn(
          "flex items-start gap-3 rounded-xl border border-dashed border-border/80 bg-muted/15 px-3 py-2.5",
          className,
        )}
      >
        <Icon className="mt-0.5 size-4 shrink-0 text-primary/80" aria-hidden="true" />
        <div className="min-w-0">
          <p className="text-xs font-medium">{title}</p>
          {detail && <p className="mt-0.5 text-[11px] text-muted-foreground">{detail}</p>}
        </div>
      </div>
    );
  }

  return (
    <div
      role="status"
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-border bg-muted/10 px-6 py-12 text-center",
        className,
      )}
    >
      <span className="relative grid size-12 place-items-center rounded-xl bg-primary/10 text-primary">
        <span
          className="absolute inset-0 animate-ping rounded-xl bg-primary/10"
          aria-hidden="true"
        />
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <h3 className="text-sm font-semibold">{title}</h3>
      {detail && <p className="max-w-md text-xs text-muted-foreground">{detail}</p>}
      {action}
    </div>
  );
}
