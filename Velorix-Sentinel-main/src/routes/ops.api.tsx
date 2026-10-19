import { createFileRoute } from "@tanstack/react-router";
import { Activity, AlertTriangle, Gauge, Timer, Waypoints } from "lucide-react";

import { DataTable, type DataTableColumn } from "@/components/common";
import { AwaitingMonitoring, HealthBadge, OpsKpi, OpsSection } from "@/components/observability";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService } from "@/services/observabilityService";
import type { ApiEndpointMetric } from "@/types/observability";

export const Route = createFileRoute("/ops/api")({
  head: () => ({
    meta: [
      { title: "API Monitoring · Velorix Sentinel" },
      {
        name: "description",
        content: "Endpoint throughput, error rates and latency across the platform API.",
      },
      { property: "og:title", content: "API Monitoring · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Endpoint throughput, error rates and latency across the platform API.",
      },
    ],
  }),
  component: ApiMonitoringPage,
});

function ApiMonitoringPage() {
  const endpoints = useObservabilityResource(observabilityService.listApiMetrics);
  const rows = endpoints.data ?? [];

  const columns: Array<DataTableColumn<ApiEndpointMetric>> = [
    {
      key: "endpoint",
      header: "Endpoint",
      sortable: true,
      sortValue: (row) => `${row.path} ${row.method}`,
      cell: (row) => (
        <span className="font-mono text-xs">
          <span className="text-primary">{row.method}</span> {row.path}
        </span>
      ),
    },
    { key: "module", header: "Module", sortable: true, cell: (row) => row.module },
    {
      key: "requests",
      header: "Requests",
      sortable: true,
      sortValue: (row) => row.requestCount ?? -1,
      cell: (row) => row.requestCount ?? "—",
    },
    {
      key: "success",
      header: "Success Rate",
      sortable: true,
      sortValue: (row) => row.successRate ?? -1,
      cell: (row) => (row.successRate != null ? `${row.successRate}%` : "—"),
    },
    {
      key: "failure",
      header: "Failure Rate",
      sortable: true,
      sortValue: (row) => row.failureRate ?? -1,
      cell: (row) => (row.failureRate != null ? `${row.failureRate}%` : "—"),
    },
    {
      key: "latency",
      header: "p95 Latency",
      sortable: true,
      sortValue: (row) => row.p95LatencyMs ?? -1,
      cell: (row) => (row.p95LatencyMs != null ? `${row.p95LatencyMs} ms` : "—"),
    },
    { key: "rateLimit", header: "Rate Limit", cell: (row) => row.rateLimit ?? "—" },
    { key: "state", header: "Health", cell: (row) => <HealthBadge state={row.state} /> },
  ];

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <OpsKpi label="Request Rate" icon={Waypoints} hint="Requests per second across the API" />
        <OpsKpi label="Success Rate" icon={Activity} hint="2xx and 3xx share of all responses" />
        <OpsKpi label="Failure Rate" icon={AlertTriangle} hint="4xx and 5xx share of responses" />
        <OpsKpi label="Avg Response Time" icon={Timer} hint="Mean server-side processing time" />
        <OpsKpi label="p95 Latency" icon={Gauge} hint="95th percentile end-to-end latency" />
      </div>

      <OpsSection
        title="Endpoint Performance"
        description="Per-endpoint throughput, reliability and latency reported by the gateway filter."
        bodyClassName={rows.length ? "p-4" : "p-4"}
      >
        {rows.length ? (
          <DataTable
            rows={rows}
            columns={columns}
            rowKey={(row) => row.id}
            isLoading={endpoints.status === "loading"}
            searchAccessor={(row) => `${row.method} ${row.path} ${row.module}`}
            searchPlaceholder="Search endpoints…"
            pageSize={15}
          />
        ) : (
          <AwaitingMonitoring detail="Endpoint metrics are produced by the API gateway request filter and exported to the metrics backend. No endpoint statistics are simulated." />
        )}
      </OpsSection>

      <OpsSection
        title="Rate Limiting & Quotas"
        description="Per-key throttling policy, burst allowance and rejection counts."
      >
        <AwaitingMonitoring detail="Rate-limit counters become available once the gateway publishes throttling metrics." />
      </OpsSection>
    </div>
  );
}
