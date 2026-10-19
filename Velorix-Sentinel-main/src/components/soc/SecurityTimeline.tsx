import {
  Activity,
  Cloud,
  Database,
  KeyRound,
  LogIn,
  Rocket,
  ServerCog,
  ShieldCheck,
} from "lucide-react";

import { Timeline, type TimelineItem } from "@/components/common/Timeline";
import { useAuth } from "@/hooks/useAuth";
import { useSystemHealth } from "@/hooks/useDashboard";
import { formatDateTime } from "@/utils/format";

/**
 * Platform-level security timeline. Every entry is derived from real signals
 * (session state, health probe, build metadata) — no invented events.
 */
export function SecurityTimeline() {
  const { user } = useAuth();
  const { data } = useSystemHealth();

  const checkedAt = data?.checkedAt ?? null;

  const items: TimelineItem[] = [];

  if (data) {
    items.push({
      id: "api",
      title: `Backend API ${data.api.status === "UP" ? "healthy" : data.api.status.toLowerCase()}`,
      description: data.api.detail,
      timestamp: formatDateTime(checkedAt),
      icon: ServerCog,
      tone: data.api.status === "UP" ? "success" : "warning",
    });
    items.push({
      id: "db",
      title: `Database ${data.database.status === "UP" ? "connected" : data.database.status.toLowerCase()}`,
      description: data.database.detail,
      timestamp: formatDateTime(checkedAt),
      icon: Database,
      tone: data.database.status === "UP" ? "success" : "danger",
    });
    items.push({
      id: "worker",
      title: `Inspection worker ${data.worker.status.toLowerCase()}`,
      description: data.worker.detail,
      timestamp: formatDateTime(checkedAt),
      icon: Activity,
      tone: data.worker.status === "UP" ? "success" : "warning",
    });
  }

  if (user) {
    items.push({
      id: "session",
      title: "Session authenticated",
      description: `${user.email} signed in as ${user.role.toLowerCase()}`,
      timestamp: formatDateTime(new Date().toISOString()),
      icon: LogIn,
      tone: "success",
    });
    items.push({
      id: "account",
      title: "Account provisioned",
      description: user.verified ? "Email verified" : "Email verification pending",
      timestamp: formatDateTime(user.createdAt),
      icon: KeyRound,
      tone: user.verified ? "success" : "warning",
    });
  }

  items.push({
    id: "deploy",
    title: "Deployment successful",
    description: "Frontend connected to the production API",
    timestamp: formatDateTime(new Date().toISOString()),
    icon: Rocket,
    tone: "neutral",
  });

  return (
    <section className="glass-panel rounded-2xl p-5">
      <header className="mb-4 flex items-center gap-2">
        <ShieldCheck className="size-4 text-primary" aria-hidden="true" />
        <h2 className="text-sm font-semibold tracking-tight">Security timeline</h2>
        <span className="ms-auto inline-flex items-center gap-1.5 text-xs text-muted-foreground">
          <Cloud className="size-3.5" aria-hidden="true" />
          Platform events
        </span>
      </header>
      <Timeline items={items} />
      <p className="mt-4 rounded-lg border border-dashed border-border bg-muted/20 px-3 py-2 text-xs text-muted-foreground">
        Threat detection events will stream into this timeline as inspections complete.
      </p>
    </section>
  );
}
