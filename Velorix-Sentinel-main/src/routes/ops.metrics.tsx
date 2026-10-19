import { createFileRoute } from "@tanstack/react-router";
import { Gauge } from "lucide-react";

import { AwaitingMonitoring, MetricPanel, OpsSection } from "@/components/observability";
import { PLATFORM_METRIC_CATALOGUE } from "@/data/observability-reference";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService } from "@/services/observabilityService";

export const Route = createFileRoute("/ops/metrics")({
  head: () => ({
    meta: [
      { title: "Platform Metrics · Velorix Sentinel" },
      {
        name: "description",
        content: "Resource utilisation, throughput and latency metrics for platform services.",
      },
      { property: "og:title", content: "Platform Metrics · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Resource utilisation, throughput and latency metrics for platform services.",
      },
    ],
  }),
  component: MetricsPage,
});

function MetricsPage() {
  const metrics = useObservabilityResource(observabilityService.listMetrics);
  const series = metrics.data?.length ? metrics.data : PLATFORM_METRIC_CATALOGUE;

  return (
    <div className="space-y-6">
      <OpsSection
        title="Platform Metrics"
        description="Resource, throughput and reliability signals scraped from the application tier."
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {series.map((metric, index) => (
            <MetricPanel key={metric.id} metric={metric} delay={index * 0.02} />
          ))}
        </div>
      </OpsSection>

      <OpsSection
        title="Time-Series Explorer"
        description="Ad-hoc querying, dashboards and range selection over collected metrics."
      >
        <AwaitingMonitoring
          icon={Gauge}
          title="Awaiting Live Platform Monitoring"
          detail="The explorer renders PromQL-style range queries once Micrometer exports to Prometheus and the /v1/ops/metrics endpoint is available. No synthetic series are drawn."
        />
      </OpsSection>
    </div>
  );
}
