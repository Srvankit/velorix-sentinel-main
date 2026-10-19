import { Activity, Cpu, Database, Server, ShieldQuestion } from "lucide-react";

import { StatusBadge } from "@/components/common/StatusBadge";
import { Skeleton } from "@/components/common/Skeletons";
import { useSystemHealth } from "@/hooks/useDashboard";
import { formatRelativeTime } from "@/utils/format";
import type { ComponentHealth } from "@/types/dashboard";

const SECURITY_TIPS = [
  "Re-run detection after tuning a rule — findings are derived data and are replaced on every pass.",
  "Investigate CRITICAL findings first; risk score saturates, so a single severe rule can dominate.",
  "Correlate top source IPs across analyses to spot infrastructure reused between campaigns.",
  "Archive captures once triaged to keep storage predictable and inspections fast.",
];

function HealthRow({ icon: Icon, health }: { icon: typeof Server; health: ComponentHealth }) {
  return (
    <li className="flex items-center gap-3 rounded-xl border border-border/70 bg-surface/40 px-3 py-2.5">
      <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
        <Icon className="size-4" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium">{health.name}</span>
        <span className="block truncate text-xs text-muted-foreground">{health.detail}</span>
      </span>
      <StatusBadge status={health.status} />
    </li>
  );
}

/** Right-hand insights rail: live platform health plus analyst guidance. */
export function InsightsPanel() {
  const { data, isLoading, isError, error } = useSystemHealth();

  return (
    <div className="space-y-6">
      <section className="glass-panel rounded-2xl p-5">
        <header className="mb-4 flex items-center gap-2">
          <Activity className="size-4 text-primary" aria-hidden="true" />
          <h2 className="text-sm font-semibold tracking-tight">System health</h2>
        </header>

        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        ) : isError || !data ? (
          <p className="text-sm text-muted-foreground">
            {error?.message ?? "Health data is unavailable right now."}
          </p>
        ) : (
          <>
            <ul className="space-y-2">
              <HealthRow icon={Server} health={data.api} />
              <HealthRow icon={Database} health={data.database} />
              <HealthRow icon={Cpu} health={data.worker} />
            </ul>
            <p className="mt-3 text-xs text-muted-foreground">
              Checked {formatRelativeTime(data.checkedAt)}
            </p>
          </>
        )}
      </section>

      <section className="glass-panel rounded-2xl p-5">
        <header className="mb-3 flex items-center gap-2">
          <ShieldQuestion className="size-4 text-primary" aria-hidden="true" />
          <h2 className="text-sm font-semibold tracking-tight">Security tips</h2>
        </header>
        <ul className="space-y-3">
          {SECURITY_TIPS.map((tip) => (
            <li key={tip} className="flex gap-2 text-sm text-muted-foreground">
              <span className="mt-2 size-1.5 shrink-0 rounded-full bg-primary" aria-hidden="true" />
              {tip}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
