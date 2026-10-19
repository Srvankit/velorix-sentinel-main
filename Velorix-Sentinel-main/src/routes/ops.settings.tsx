import { createFileRoute } from "@tanstack/react-router";

import { ComingSoonTag, OpsSection, StatRow } from "@/components/observability";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import {
  observabilityService,
  OBSERVABILITY_ALERTING_READY,
  OBSERVABILITY_BACKEND_READY,
  OBSERVABILITY_WRITE_READY,
} from "@/services/observabilityService";

export const Route = createFileRoute("/ops/settings")({
  head: () => ({
    meta: [
      { title: "Observability Settings · Velorix Sentinel" },
      {
        name: "description",
        content: "Telemetry collection, retention and exporter configuration.",
      },
      { property: "og:title", content: "Observability Settings · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Telemetry collection, retention and exporter configuration.",
      },
    ],
  }),
  component: ObservabilitySettingsPage,
});

function ObservabilitySettingsPage() {
  const settings = useObservabilityResource(observabilityService.getSettings);
  const data = settings.data;

  const flags = [
    {
      label: "Monitoring Backend",
      enabled: OBSERVABILITY_BACKEND_READY,
      hint: "Health, metrics, logs and database telemetry endpoints",
    },
    {
      label: "Operational Controls",
      enabled: OBSERVABILITY_WRITE_READY,
      hint: "Rollback and restore mutations",
    },
    {
      label: "Alert Delivery",
      enabled: OBSERVABILITY_ALERTING_READY,
      hint: "Email, Slack and webhook notification dispatch",
    },
  ];

  return (
    <div className="space-y-6">
      <OpsSection
        title="Platform Capability Flags"
        description="Which observability capabilities are wired to the backend in this deployment."
      >
        <div className="space-y-2">
          {flags.map((flag) => (
            <div
              key={flag.label}
              className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5"
            >
              <div className="min-w-0">
                <p className="truncate text-xs font-medium">{flag.label}</p>
                <p className="mt-0.5 text-[11px] text-muted-foreground">{flag.hint}</p>
              </div>
              <span
                className={
                  flag.enabled
                    ? "rounded-md bg-emerald-500/10 px-2 py-0.5 font-mono text-[10px] text-emerald-400"
                    : "rounded-md bg-muted/40 px-2 py-0.5 font-mono text-[10px] text-muted-foreground"
                }
              >
                {flag.enabled ? "ENABLED" : "NOT CONNECTED"}
              </span>
            </div>
          ))}
        </div>
      </OpsSection>

      <div className="grid gap-4 xl:grid-cols-2">
        <OpsSection
          title="Collection Settings"
          description="Sampling, retention and refresh behaviour for platform telemetry."
          actions={<ComingSoonTag />}
        >
          <div className="space-y-2">
            <StatRow
              label="Metric Collection"
              value={data ? (data.metricCollectionEnabled ? "Enabled" : "Disabled") : undefined}
              hint="Whether Micrometer collection is active"
            />
            <StatRow
              label="Scrape Interval"
              value={data?.scrapeIntervalSeconds ? `${data.scrapeIntervalSeconds}s` : undefined}
              hint="Seconds between metric scrapes"
            />
            <StatRow
              label="Log Retention"
              value={data?.logRetentionDays ? `${data.logRetentionDays} days` : undefined}
              hint="How long log lines remain searchable"
            />
            <StatRow
              label="Audit Retention"
              value={data?.auditRetentionDays ? `${data.auditRetentionDays} days` : undefined}
              hint="How long audit records are preserved"
            />
            <StatRow
              label="Trace Sampling Rate"
              value={data?.tracingSampleRate != null ? `${data.tracingSampleRate}%` : undefined}
              hint="Share of requests captured as distributed traces"
            />
          </div>
        </OpsSection>

        <OpsSection
          title="Exporters & Providers"
          description="Where telemetry is shipped once collection is enabled."
          actions={<ComingSoonTag />}
        >
          <div className="space-y-2">
            <StatRow label="Metrics Provider" hint="Micrometer registry target, e.g. Prometheus" />
            <StatRow label="Log Provider" hint="Aggregation backend, e.g. Loki or Elastic" />
            <StatRow label="Trace Provider" hint="OpenTelemetry collector endpoint" />
            <StatRow label="Dashboard Provider" hint="Visualisation layer, e.g. Grafana" />
            <StatRow
              label="Tracing"
              value={data ? (data.tracingEnabled ? "Enabled" : "Disabled") : undefined}
              hint="OpenTelemetry instrumentation state"
            />
          </div>
        </OpsSection>
      </div>

      <OpsSection
        title="Health Check Configuration"
        description="Probe intervals, timeouts and failure thresholds per monitored service."
        actions={<ComingSoonTag />}
      >
        <div className="space-y-2">
          <StatRow label="Probe Interval" hint="Seconds between successive health checks" />
          <StatRow label="Probe Timeout" hint="Seconds before a probe is considered failed" />
          <StatRow label="Unhealthy Threshold" hint="Consecutive failures before alerting" />
          <StatRow label="Recovery Threshold" hint="Consecutive successes before clearing" />
        </div>
      </OpsSection>
    </div>
  );
}
