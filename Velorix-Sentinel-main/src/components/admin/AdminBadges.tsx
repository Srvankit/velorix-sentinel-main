import { CheckCircle2, CircleDashed, Clock, TriangleAlert } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import type { IntegrationState, PolicyState } from "@/types/admin";

const POLICY_TONE: Record<PolicyState, { tone: string; icon: LucideIcon; label: string }> = {
  ENFORCED: {
    tone: "border-emerald-500/30 bg-emerald-500/10 text-emerald-400",
    icon: CheckCircle2,
    label: "Enforced",
  },
  MONITORED: {
    tone: "border-sky-500/30 bg-sky-500/10 text-sky-400",
    icon: Clock,
    label: "Monitored",
  },
  DISABLED: {
    tone: "border-primary/40 bg-primary/10 text-primary",
    icon: TriangleAlert,
    label: "Disabled",
  },
  NOT_CONFIGURED: {
    tone: "border-border bg-muted/30 text-muted-foreground",
    icon: CircleDashed,
    label: "Awaiting Live Data",
  },
};

export function PolicyStateBadge({ state }: { state: PolicyState }) {
  const { tone, icon: Icon, label } = POLICY_TONE[state];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-medium",
        tone,
      )}
    >
      <Icon className="size-3" aria-hidden="true" />
      {label}
    </span>
  );
}

const INTEGRATION_TONE: Record<IntegrationState, { tone: string; label: string }> = {
  CONNECTED: {
    tone: "border-emerald-500/30 bg-emerald-500/10 text-emerald-400",
    label: "Connected",
  },
  AVAILABLE: { tone: "border-sky-500/30 bg-sky-500/10 text-sky-400", label: "Available" },
  COMING_SOON: { tone: "border-border bg-muted/30 text-muted-foreground", label: "Coming Soon" },
  ERROR: { tone: "border-primary/40 bg-primary/10 text-primary", label: "Error" },
};

export function IntegrationStateBadge({ state }: { state: IntegrationState }) {
  const { tone, label } = INTEGRATION_TONE[state];
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
