import { createFileRoute } from "@tanstack/react-router";
import { Boxes } from "lucide-react";

import { AwaitingMonitoring, HealthBadge, OpsSection, StatRow } from "@/components/observability";
import { ENVIRONMENT_CATALOGUE } from "@/data/observability-reference";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService } from "@/services/observabilityService";

export const Route = createFileRoute("/ops/environments")({
  head: () => ({
    meta: [
      { title: "Environment Management · Velorix Sentinel" },
      {
        name: "description",
        content: "Development, staging and production environment status and configuration.",
      },
      { property: "og:title", content: "Environment Management · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Development, staging and production environment status and configuration.",
      },
    ],
  }),
  component: EnvironmentsPage,
});

function EnvironmentsPage() {
  const environments = useObservabilityResource(observabilityService.listEnvironments);
  const rows = environments.data?.length ? environments.data : ENVIRONMENT_CATALOGUE;

  return (
    <div className="space-y-6">
      <div className="grid gap-4 xl:grid-cols-2">
        {rows.map((environment) => (
          <OpsSection
            key={environment.name}
            title={environment.label}
            description={environment.purpose}
            actions={<HealthBadge state={environment.state} />}
          >
            <div className="space-y-2">
              <StatRow label="Version" value={environment.version ?? undefined} />
              <StatRow label="Region" value={environment.region ?? undefined} />
              <StatRow
                label="Last Deployment"
                value={
                  environment.lastDeployedAt
                    ? new Date(environment.lastDeployedAt).toLocaleString()
                    : undefined
                }
              />
              <StatRow label="API Base URL" value={environment.apiBaseUrl ?? undefined} />
              <StatRow label="Configuration Drift" hint="Difference against the baseline config" />
            </div>
          </OpsSection>
        ))}
      </div>

      <OpsSection
        title="Configuration Management"
        description="Per-environment settings, feature flags and secret references."
      >
        <AwaitingMonitoring
          icon={Boxes}
          detail="Environment configuration is resolved server-side and never exposed to the browser. Values appear here once the configuration API ships with redaction."
        />
      </OpsSection>
    </div>
  );
}
