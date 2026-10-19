import { createFileRoute } from "@tanstack/react-router";
import { Database, GitBranch, HardDrive, Timer } from "lucide-react";

import { AwaitingMonitoring, OpsKpi, OpsSection, StatRow } from "@/components/observability";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService } from "@/services/observabilityService";

export const Route = createFileRoute("/ops/database")({
  head: () => ({
    meta: [
      { title: "Database Monitoring · Velorix Sentinel" },
      {
        name: "description",
        content: "PostgreSQL connection pool, query performance and replication health.",
      },
      { property: "og:title", content: "Database Monitoring · Velorix Sentinel" },
      {
        property: "og:description",
        content: "PostgreSQL connection pool, query performance and replication health.",
      },
    ],
  }),
  component: DatabaseMonitoringPage,
});

function DatabaseMonitoringPage() {
  const db = useObservabilityResource(observabilityService.getDatabaseHealth);
  const data = db.data;
  const loading = db.status === "loading";

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <OpsKpi
          label="Connection Status"
          icon={Database}
          state={data?.state ?? "UNKNOWN"}
          isLoading={loading}
          hint="Pool reachability against the primary"
        />
        <OpsKpi
          label="Active Connections"
          icon={GitBranch}
          value={
            data?.activeConnections != null && data?.maxConnections != null
              ? `${data.activeConnections}/${data.maxConnections}`
              : null
          }
          isLoading={loading}
          hint="HikariCP pool utilisation"
        />
        <OpsKpi
          label="Avg Query Time"
          icon={Timer}
          value={data?.avgQueryMs ?? null}
          suffix=" ms"
          isLoading={loading}
          hint="Mean statement execution time"
        />
        <OpsKpi
          label="Storage Used"
          icon={HardDrive}
          value={data?.storageUsedPercent ?? null}
          suffix="%"
          isLoading={loading}
          hint="Volume consumption against provisioned capacity"
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <OpsSection
          title="Instance Details"
          description="Engine, version and physical footprint of the primary database."
        >
          <div className="space-y-2">
            <StatRow label="Engine" value={data?.engine ?? undefined} />
            <StatRow label="Version" value={data?.version ?? undefined} />
            <StatRow label="Database Size" value={formatBytes(data?.sizeBytes)} />
            <StatRow label="Slow Queries (24h)" value={data?.slowQueries ?? undefined} />
          </div>
        </OpsSection>

        <OpsSection
          title="Replication & Backup"
          description="Redundancy posture and most recent protected recovery point."
        >
          <div className="space-y-2">
            <StatRow
              label="Replication State"
              value={
                data?.replicationState && data.replicationState !== "UNKNOWN"
                  ? data.replicationState
                  : undefined
              }
            />
            <StatRow
              label="Replication Lag"
              value={
                data?.replicationLagSeconds != null ? `${data.replicationLagSeconds}s` : undefined
              }
            />
            <StatRow
              label="Last Backup"
              value={data?.lastBackupAt ? new Date(data.lastBackupAt).toLocaleString() : undefined}
            />
            <StatRow label="Point-in-Time Recovery" />
          </div>
        </OpsSection>
      </div>

      <OpsSection
        title="Query Insights"
        description="Top statements by total time, call count and rows examined."
      >
        <AwaitingMonitoring detail="Query insights require pg_stat_statements exposure through the monitoring endpoint. No query plans or timings are simulated." />
      </OpsSection>
    </div>
  );
}

function formatBytes(bytes?: number | null) {
  if (bytes == null) return undefined;
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(1)} ${units[unit]}`;
}
