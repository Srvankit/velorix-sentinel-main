import { BadgeCheck, Bookmark, Clock, Inbox, ListTodo, Pin, Timer } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { AwaitingTelemetry } from "./AwaitingTelemetry";
import { SkeletonStat } from "@/components/common";
import { useAnalystWorkspace } from "@/hooks/useAnalystWorkspace";
import { useSocResource } from "@/hooks/useSocResource";
import { socService } from "@/services/socService";
import { cn } from "@/lib/utils";

/**
 * Analyst productivity widgets. Backend counters come from `/v1/soc/productivity`;
 * local task counts come from the on-device analyst workspace store.
 */
export function ProductivityPanel({ className }: { className?: string }) {
  const productivity = useSocResource((signal) => socService.getProductivity(signal));
  const { tasks, notes } = useAnalystWorkspace();
  const data = productivity.data;
  const openTasks = tasks.filter((task) => !task.done).length;

  if (productivity.status === "loading") {
    return (
      <div className={cn("grid gap-2 sm:grid-cols-2", className)}>
        <SkeletonStat />
        <SkeletonStat />
      </div>
    );
  }

  return (
    <div className={cn("space-y-3", className)}>
      <div className="grid gap-2 sm:grid-cols-2">
        <Stat icon={Inbox} label="Assigned cases" value={data?.assignedCases} />
        <Stat icon={BadgeCheck} label="Resolved today" value={data?.resolvedToday} />
        <Stat
          icon={Timer}
          label="Avg. resolution"
          value={data?.averageResolutionMinutes}
          suffix=" min"
        />
        <Stat icon={Clock} label="Pending reviews" value={data?.pendingReviews} />
        <Stat icon={ListTodo} label="Open tasks (local)" value={openTasks} />
        <Stat icon={Bookmark} label="Notes (local)" value={notes.length} />
        <Stat icon={Pin} label="Pinned investigations" value={data?.pinnedInvestigations} />
      </div>

      {!data && (
        <AwaitingTelemetry
          compact
          title="Case metrics are not connected."
          detail="Assigned, resolved and review counters populate from /v1/soc/productivity. Local task and note counts are real and stored on this device."
        />
      )}
    </div>
  );
}

function Stat({
  icon: Icon,
  label,
  value,
  suffix = "",
}: {
  icon: LucideIcon;
  label: string;
  value: number | null | undefined;
  suffix?: string;
}) {
  return (
    <div className="rounded-xl border border-border/70 bg-card/40 p-3">
      <span className="flex items-center gap-1.5 text-[10px] tracking-wide text-muted-foreground uppercase">
        <Icon className="size-3 shrink-0" aria-hidden="true" />
        <span className="truncate">{label}</span>
      </span>
      <p className="mt-1 font-mono text-lg leading-none font-semibold">
        {value === null || value === undefined ? (
          <span className="text-base text-muted-foreground">—</span>
        ) : (
          <>
            {value}
            <span className="text-xs text-muted-foreground">{suffix}</span>
          </>
        )}
      </p>
    </div>
  );
}
