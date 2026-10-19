import { Crown } from "lucide-react";

import { cn } from "@/lib/utils";

interface EnterpriseOnlyProps {
  title?: string;
  detail?: string;
  className?: string;
}

/**
 * Honest placeholder for commercial surfaces (subscription, licensing, billing)
 * that are not provisioned in this deployment.
 */
export function EnterpriseOnly({
  title = "Available in Enterprise Edition",
  detail = "Subscription, licensing and billing are provisioned per tenant. No plan, invoice or payment data exists in this deployment.",
  className,
}: EnterpriseOnlyProps) {
  return (
    <div
      role="status"
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-primary/30 bg-primary/5 px-6 py-10 text-center",
        className,
      )}
    >
      <span className="grid size-11 place-items-center rounded-xl bg-primary/10 text-primary">
        <Crown className="size-5" aria-hidden="true" />
      </span>
      <h3 className="text-sm font-semibold">{title}</h3>
      <p className="max-w-md text-xs text-muted-foreground">{detail}</p>
    </div>
  );
}
