import type { LucideIcon } from "lucide-react";
import { Cpu, Database, HardDrive, Radar, Server, ShieldCheck } from "lucide-react";

import { Skeleton } from "@/components/common/Skeletons";
import { cn } from "@/lib/utils";
import { useSystemHealth } from "@/hooks/useDashboard";
import { formatRelativeTime } from "@/utils/format";
import type { ComponentStatus } from "@/types/dashboard";

type ServiceState = ComponentStatus | "UNKNOWN";

const STATE_META: Record<ServiceState, { label: string; dot: string; text: string; ring: string }> =
  {
    UP: {
      label: "Healthy",
      dot: "bg-success",
      text: "text-success",
      ring: "border-success/40 bg-success/10",
    },
    DEGRADED: {
      label: "Warning",
      dot: "bg-warning",
      text: "text-warning",
      ring: "border-warning/40 bg-warning/10",
    },
    DOWN: {
      label: "Offline",
      dot: "bg-destructive",
      text: "text-destructive",
      ring: "border-destructive/40 bg-destructive/10",
    },
    UNKNOWN: {
      label: "Awaiting Data",
      dot: "bg-muted-foreground/60",
      text: "text-muted-foreground",
      ring: "border-border bg-muted/30",
    },
  };

function ServiceRow({
  icon: Icon,
  name,
  detail,
  state,
}: {
  icon: LucideIcon;
  name: string;
  detail: string;
  state: ServiceState;
}) {
  const meta = STATE_META[state];
  return (
    <li className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 rounded-xl border border-border/70 bg-surface/40 px-3 py-2.5 transition-colors hover:border-primary/25 hover:bg-surface/70">
      <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
        <Icon className="size-4" aria-hidden="true" />
      </span>
      <span className="min-w-0">
        <span className="block truncate text-sm font-medium">{name}</span>
        <span className="block truncate text-xs text-muted-foreground">{detail}</span>
      </span>
      <span
        className={cn(
          "inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium",
          meta.ring,
          meta.text,
        )}
      >
        <span className="relative flex size-1.5">
          {state !== "UNKNOWN" && (
            <span
              className={cn("absolute inline-flex size-full animate-ping rounded-full", meta.dot)}
            />
          )}
          <span className={cn("relative inline-flex size-1.5 rounded-full", meta.dot)} />
        </span>
        {meta.label}
      </span>
    </li>
  );
}

/** Animated health board for every platform service. */
export function SystemHealthPanel() {
  const { data, isLoading, isError } = useSystemHealth();

  const state = (value: ComponentStatus | undefined): ServiceState => value ?? "UNKNOWN";

  return (
    <section className="glass-panel rounded-2xl p-5">
      <header className="mb-4 flex items-center gap-2">
        <ShieldCheck className="size-4 text-primary" aria-hidden="true" />
        <h2 className="text-sm font-semibold tracking-tight">System health</h2>
      </header>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={index} className="h-14 w-full" />
          ))}
        </div>
      ) : (
        <>
          <ul className="space-y-2">
            <ServiceRow
              icon={Server}
              name="Backend API"
              detail={data?.api.detail ?? "Health probe unavailable"}
              state={state(data?.api.status)}
            />
            <ServiceRow
              icon={Database}
              name="Database"
              detail={data?.database.detail ?? "Health probe unavailable"}
              state={state(data?.database.status)}
            />
            <ServiceRow
              icon={ShieldCheck}
              name="Authentication"
              detail={
                isError ? "Unable to reach the identity service" : "JWT issuance and refresh active"
              }
              state={isError ? "DOWN" : data ? "UP" : "UNKNOWN"}
            />
            <ServiceRow
              icon={HardDrive}
              name="Storage"
              detail="Capture object store — status endpoint pending"
              state="UNKNOWN"
            />
            <ServiceRow
              icon={Cpu}
              name="Inspection worker"
              detail={data?.worker.detail ?? "Health probe unavailable"}
              state={state(data?.worker.status)}
            />
            <ServiceRow
              icon={Radar}
              name="Threat engine"
              detail="Rule evaluator bound to the inspection worker"
              state={state(data?.worker.status)}
            />
          </ul>
          <p className="mt-3 text-xs text-muted-foreground">
            Checked {formatRelativeTime(data?.checkedAt)}
          </p>
        </>
      )}
    </section>
  );
}
