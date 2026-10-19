import { CheckCircle2, CircleAlert } from "lucide-react";

import { EmptyState } from "@/components/common/EmptyState";
import { SkeletonList } from "@/components/common/Skeletons";
import { Timeline, type TimelineItem } from "@/components/common/Timeline";
import { useActivityFeed } from "@/hooks/useDashboard";
import type { ActivityEntry } from "@/types/dashboard";
import { formatDateTime } from "@/utils/format";

function toTimelineItem(entry: ActivityEntry): TimelineItem {
  return {
    id: entry.id,
    title: entry.activityType.toLowerCase().replace(/_/g, " "),
    description: entry.description ?? entry.ipAddress ?? undefined,
    timestamp: formatDateTime(entry.occurredAt),
    icon: entry.successful ? CheckCircle2 : CircleAlert,
    tone: entry.successful ? "success" : "danger",
  };
}

/** Audit trail of account and platform events from `/dashboard/activity`. */
export function ActivityTimeline({ limit = 10 }: { limit?: number }) {
  const { data, isLoading, isError, error } = useActivityFeed(limit);

  return (
    <section className="glass-panel rounded-2xl p-5">
      <header className="mb-4">
        <h2 className="text-sm font-semibold tracking-tight">Activity timeline</h2>
        <p className="text-xs text-muted-foreground">Recent account and platform events</p>
      </header>

      {isLoading ? (
        <SkeletonList items={5} />
      ) : isError ? (
        <p className="text-sm text-muted-foreground">{error?.message}</p>
      ) : !data || data.length === 0 ? (
        <EmptyState
          title="No activity recorded"
          description="Sign-ins, uploads and detection runs appear here as they happen."
          className="py-10"
        />
      ) : (
        <Timeline items={data.map(toTimelineItem)} />
      )}
    </section>
  );
}
