import { Link } from "@tanstack/react-router";
import {
  CheckCircle2,
  CircleAlert,
  History,
  LogIn,
  LogOut,
  ShieldAlert,
  UploadCloud,
  UserRoundCog,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { SkeletonList } from "@/components/common/Skeletons";
import { Timeline, type TimelineItem } from "@/components/common/Timeline";
import { Button } from "@/components/ui/button";
import { useActivityFeed } from "@/hooks/useDashboard";
import type { ActivityEntry } from "@/types/dashboard";
import { formatDateTime } from "@/utils/format";

const ICONS: Record<string, LucideIcon> = {
  LOGIN: LogIn,
  LOGOUT: LogOut,
  PROFILE_UPDATE: UserRoundCog,
  FILE_UPLOAD: UploadCloud,
  THREAT_DETECTED: ShieldAlert,
};

function toTimelineItem(entry: ActivityEntry): TimelineItem {
  return {
    id: entry.id,
    title: entry.activityType.toLowerCase().replace(/_/g, " "),
    description: entry.description ?? entry.ipAddress ?? undefined,
    timestamp: formatDateTime(entry.occurredAt),
    icon: ICONS[entry.activityType] ?? (entry.successful ? CheckCircle2 : CircleAlert),
    tone: entry.successful ? "success" : "danger",
  };
}

const UPCOMING = [
  { icon: UploadCloud, label: "Capture uploads" },
  { icon: ShieldAlert, label: "Threat analysis" },
  { icon: History, label: "Generated reports" },
];

/** Audit trail of account and platform events from `/dashboard/activity`. */
export function RecentActivityPanel({ limit = 10 }: { limit?: number }) {
  const { data, isLoading, isError, error } = useActivityFeed(limit);

  return (
    <section className="glass-panel rounded-2xl p-5">
      <header className="mb-4 grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2">
        <div className="min-w-0">
          <h2 className="truncate text-sm font-semibold tracking-tight">Recent activity</h2>
          <p className="truncate text-xs text-muted-foreground">
            Sign-ins, profile changes and platform events
          </p>
        </div>
        <Button variant="ghost" size="sm" asChild>
          <Link to="/history">Full audit log</Link>
        </Button>
      </header>

      {isLoading ? (
        <SkeletonList items={5} />
      ) : isError ? (
        <p className="text-sm text-muted-foreground">{error?.message}</p>
      ) : !data || data.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-muted/15 px-5 py-8 text-center">
          <span className="mx-auto grid size-11 place-items-center rounded-xl bg-primary/10 text-primary">
            <History className="size-5" aria-hidden="true" />
          </span>
          <p className="mt-3 text-sm font-medium">No activity recorded yet</p>
          <p className="mx-auto mt-1 max-w-sm text-xs text-muted-foreground">
            Your audit trail is empty. Events are written the moment they happen.
          </p>
          <ul className="mt-4 flex flex-wrap justify-center gap-2">
            {UPCOMING.map((item) => (
              <li
                key={item.label}
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface/50 px-2.5 py-1 text-[11px] text-muted-foreground"
              >
                <item.icon className="size-3" aria-hidden="true" />
                {item.label}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <Timeline items={data.map(toTimelineItem)} />
      )}
    </section>
  );
}
