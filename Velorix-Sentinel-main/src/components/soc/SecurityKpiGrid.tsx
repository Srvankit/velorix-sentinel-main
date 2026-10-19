import {
  Activity,
  Boxes,
  Clock,
  Database,
  FileStack,
  Flame,
  Gauge,
  Layers,
  Network,
  ShieldAlert,
  ShieldCheck,
  Signal,
  Users,
} from "lucide-react";

import { ErrorState } from "@/components/common/ErrorState";
import { useDashboardSummary, useSystemHealth } from "@/hooks/useDashboard";
import { formatNumber } from "@/utils/format";
import { MetricCard } from "./MetricCard";

/**
 * Twelve-tile security KPI wall.
 *
 * Tiles backed by `/dashboard/summary` and `/dashboard/system-health` show real
 * values; everything else is explicitly marked "Awaiting Data".
 */
export function SecurityKpiGrid() {
  const { data, isLoading, isError, error, refetch } = useDashboardSummary();
  const health = useSystemHealth();

  if (isError) {
    return <ErrorState error={error} onRetry={() => void refetch()} />;
  }

  const n = (input: number | undefined) => (input === undefined ? undefined : formatNumber(input));
  const mediumThreats =
    data === undefined
      ? undefined
      : Math.max(0, data.threatsDetected - data.criticalThreats - data.highThreats);

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
      <MetricCard
        label="Total investigations"
        value={n(data?.totalAnalyses)}
        hint={data ? `${formatNumber(data.completedAnalyses)} completed` : undefined}
        icon={Layers}
        isLoading={isLoading}
        delay={0}
      />
      <MetricCard
        label="Threats detected"
        value={n(data?.threatsDetected)}
        hint="Across all completed inspections"
        icon={ShieldAlert}
        tone="warning"
        isLoading={isLoading}
        delay={0.02}
      />
      <MetricCard
        label="Critical threats"
        value={n(data?.criticalThreats)}
        hint="Require immediate triage"
        icon={Flame}
        tone="danger"
        isLoading={isLoading}
        delay={0.04}
      />
      <MetricCard
        label="High threats"
        value={n(data?.highThreats)}
        hint="Escalate within the shift"
        icon={ShieldAlert}
        tone="danger"
        isLoading={isLoading}
        delay={0.06}
      />
      <MetricCard
        label="Medium threats"
        value={n(mediumThreats)}
        hint="Derived from total minus critical and high"
        icon={Signal}
        tone="warning"
        isLoading={isLoading}
        delay={0.08}
      />
      <MetricCard
        label="Low threats"
        icon={Signal}
        tone="info"
        awaiting
        hint="Severity split endpoint pending"
        isLoading={isLoading}
        delay={0.1}
      />
      <MetricCard
        label="Active investigations"
        value={n(data?.activeInvestigations)}
        hint={data ? `${formatNumber(data.runningAnalyses)} inspections running` : undefined}
        icon={Activity}
        tone="warning"
        isLoading={isLoading}
        delay={0.12}
      />
      <MetricCard
        label="Security score"
        value={data ? `${data.securityScore}` : undefined}
        hint="100 = no residual risk"
        icon={ShieldCheck}
        tone={data && data.securityScore < 60 ? "danger" : "success"}
        isLoading={isLoading}
        delay={0.14}
      />
      <MetricCard
        label="Files processed"
        value={n(data?.filesUploaded)}
        hint="Captures accepted by the platform"
        icon={FileStack}
        isLoading={isLoading}
        delay={0.16}
      />
      <MetricCard
        label="Packet count"
        value={n(data?.packetsInspected)}
        hint="Packets decoded by the inspection engine"
        icon={Network}
        tone="info"
        isLoading={isLoading}
        delay={0.18}
      />
      <MetricCard
        label="Database health"
        value={health.data ? health.data.database.status : undefined}
        hint={health.data?.database.detail}
        icon={Database}
        tone={
          health.data?.database.status === "UP"
            ? "success"
            : health.data?.database.status === "DEGRADED"
              ? "warning"
              : health.data
                ? "danger"
                : "default"
        }
        isLoading={health.isLoading}
        awaiting={!health.isLoading && !health.data}
        delay={0.2}
      />
      <MetricCard
        label="Active users"
        icon={Users}
        awaiting
        hint="Presence telemetry not yet exposed"
        delay={0.22}
      />
      <MetricCard
        label="API requests"
        icon={Boxes}
        awaiting
        hint="Gateway metrics endpoint pending"
        delay={0.24}
      />
      <MetricCard
        label="Avg. response time"
        icon={Clock}
        awaiting
        hint="Latency histogram pending"
        delay={0.26}
      />
      <MetricCard
        label="System uptime"
        icon={Gauge}
        awaiting
        hint="Uptime probe pending"
        delay={0.28}
      />
    </div>
  );
}
