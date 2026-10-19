import { createFileRoute } from "@tanstack/react-router";
import {
  Activity,
  AlarmClock,
  CheckCircle2,
  Flame,
  Gauge,
  Inbox,
  LayoutList,
  ShieldAlert,
  Timer,
} from "lucide-react";

import {
  AwaitingBackend,
  CommandCenter,
  CreateCaseDialog,
  IncidentCard,
  IncidentMetric,
  IncidentTable,
  RiskDistribution,
} from "@/components/incidents";
import { PageHeader } from "@/components/common";
import { useIncidentResource } from "@/hooks/useIncidentResource";
import { incidentService } from "@/services/incidentService";

export const Route = createFileRoute("/incidents/")({
  head: () => ({
    meta: [
      { title: "Incident Response · Velorix Sentinel" },
      {
        name: "description",
        content:
          "Incident response command centre covering open cases, severity and response posture.",
      },
      { property: "og:title", content: "Incident Response · Velorix Sentinel" },
      {
        property: "og:description",
        content:
          "Incident response command centre covering open cases, severity and response posture.",
      },
    ],
  }),
  component: IncidentCenterPage,
});

function formatDuration(minutes: number | null | undefined) {
  if (minutes === null || minutes === undefined) return null;
  if (minutes < 60) return `${Math.round(minutes)}m`;
  const hours = minutes / 60;
  if (hours < 24) return `${hours.toFixed(1)}h`;
  return `${(hours / 24).toFixed(1)}d`;
}

function IncidentCenterPage() {
  const metrics = useIncidentResource((signal) => incidentService.getMetrics(signal));
  const queue = useIncidentResource((signal) => incidentService.getQueue(signal));

  const data = metrics.data;
  const loading = metrics.status === "loading";

  return (
    <div className="space-y-6">
      <PageHeader
        title="Incident Center"
        description="Executive view of open incidents, investigation load and response performance."
        actions={<CreateCaseDialog />}
      />

      {metrics.status === "error" && (
        <AwaitingBackend
          compact
          icon={ShieldAlert}
          title={`Incident metrics unavailable: ${metrics.error}`}
        />
      )}

      <section
        aria-label="Incident key metrics"
        className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"
      >
        <IncidentMetric
          label="Open Incidents"
          value={loading ? null : (data?.open ?? null)}
          icon={Inbox}
          delay={0}
          hint="Cases not yet resolved or closed"
        />
        <IncidentMetric
          label="Critical Incidents"
          value={loading ? null : (data?.critical ?? null)}
          icon={Flame}
          tone="critical"
          delay={0.05}
          hint="Severity CRITICAL, all statuses"
        />
        <IncidentMetric
          label="Active Investigations"
          value={loading ? null : (data?.investigating ?? null)}
          icon={Activity}
          delay={0.1}
          hint="Currently being worked by an analyst"
        />
        <IncidentMetric
          label="Resolved Cases"
          value={loading ? null : (data?.resolved ?? null)}
          icon={CheckCircle2}
          tone="positive"
          delay={0.15}
          hint="Closed within the reporting window"
        />
        <IncidentMetric
          label="Mean Time to Detect"
          value={formatDuration(data?.mttdMinutes)}
          icon={Timer}
          delay={0.2}
          hint="Compromise to detection"
        />
        <IncidentMetric
          label="Mean Time to Respond"
          value={formatDuration(data?.mttrMinutes)}
          icon={AlarmClock}
          delay={0.25}
          hint="Detection to first response action"
        />
        <IncidentMetric
          label="Queue Depth"
          value={loading ? null : (data?.queueDepth ?? null)}
          icon={LayoutList}
          delay={0.3}
          hint="Awaiting triage assignment"
        />
        <IncidentMetric
          label="Detection Coverage"
          value={null}
          icon={Gauge}
          delay={0.35}
          hint="Reported once the response backend is connected"
        />
      </section>

      <div className="grid gap-4 xl:grid-cols-3">
        <IncidentCard
          title="Risk Distribution"
          description="Open incidents by severity"
          icon={Gauge}
          className="xl:col-span-1"
        >
          <RiskDistribution distribution={data?.bySeverity ?? null} />
        </IncidentCard>

        <IncidentCard
          title="Command Center"
          description="Analyst quick actions"
          icon={ShieldAlert}
          className="xl:col-span-2"
        >
          <CommandCenter />
        </IncidentCard>
      </div>

      <IncidentCard
        title="Investigation Queue"
        description="Unassigned and in-progress cases in priority order"
        icon={LayoutList}
      >
        <IncidentTable
          incidents={queue.data ?? []}
          isLoading={queue.status === "loading"}
          awaiting={queue.status === "awaiting"}
          pageSize={8}
        />
      </IncidentCard>
    </div>
  );
}
