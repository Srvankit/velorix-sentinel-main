import { ShieldCheck } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface AwaitingBackendProps {
  title?: string;
  detail?: string;
  icon?: LucideIcon;
  action?: ReactNode;
  className?: string;
  compact?: boolean;
}

/**
 * Canonical honest empty state for every incident-response surface.
 * No incidents, analysts or cases are ever fabricated for demonstration.
 */
export function AwaitingBackend({
  title = "No incidents have been detected.",
  detail = "Incident data appears here once the response backend is connected. Nothing on this screen is simulated.",
  icon: Icon = ShieldCheck,
  action,
  className,
  compact = false,
}: AwaitingBackendProps) {
  if (compact) {
    return (
      <div
        role="status"
        className={cn(
          "flex items-start gap-3 rounded-xl border border-dashed border-border/80 bg-muted/20 px-3 py-2.5 text-muted-foreground",
          className,
        )}
      >
        <Icon className="mt-0.5 size-4 shrink-0 text-primary/80" aria-hidden="true" />
        <p className="text-xs font-medium text-foreground">{title}</p>
      </div>
    );
  }

  return (
    <div
      role="status"
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-border bg-muted/10 px-6 py-14 text-center",
        className,
      )}
    >
      <span className="relative grid size-12 place-items-center rounded-xl bg-primary/10 text-primary">
        <Icon className="size-5" aria-hidden="true" />
        <span
          className="absolute inset-0 animate-ping rounded-xl bg-primary/10"
          aria-hidden="true"
        />
      </span>
      <h3 className="text-base font-semibold">{title}</h3>
      <p className="max-w-md text-sm text-muted-foreground">{detail}</p>
      {action && <div className="pt-2">{action}</div>}
    </div>
  );
}
