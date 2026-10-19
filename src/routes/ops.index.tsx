import { createFileRoute } from "@tanstack/react-router";
import {
  Activity,
  BrainCircuit,
  Cpu,
  Database,
  Fingerprint,
  Gauge,
  HardDrive,
  KeyRound,
  Layers,
  Network,
  Radar,
  ServerCog,
  ShieldAlert,
  Siren,
  Waypoints,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import {
  AwaitingMonitoring,
  OpsKpi,
  OpsSection,
  ServiceStatusCard,
  StatRow,
} from "@/components/observability";
import { MONITORED_SERVICES } from "@/data/observability-reference";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService } from "@/services/observabilityService";
import type { ServiceHealth } from "@/types/observability";

export const Route = createFileRoute("/ops/")({
  head: () => ({
    meta: [
      { title: "Platform Observability · Velorix Sentinel" },
      {
        name: "description",
        content: "Health, performance and operational status of the Velorix Sentinel platform.",
      },
      { property: "og:title", content: "Platform Observability · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Health, performance and operational status of the Velorix Sentinel platform.",
      },
    ],
  }),
  component: OperationsDashboard,
});

const SERVICE_ICONS: Record<string, LucideIcon> = {
  frontend: Layers,
  backend: ServerCog,
  database: Database,
  authentication: Fingerprint,
  "api-gateway": Waypoints,
  "inspection-worker": Radar,
  "threat-engine": ShieldAlert,
  "ai-services": BrainCircuit,
  storage: HardDrive,
  licensing: KeyRound,
};

function OperationsDashboard() {
  const health = useObservabilityResource(observabilityService.getHealth);
  const loading = health.status === "loading";

  const services: ServiceHealth[] = health.data?.services?.length
    ? health.data.services
    : MONITORED_SERVICES;

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <OpsKpi
          label="Platform Health"
          icon={Gauge}
          value={null}
          state={health.data?.state ?? "UNKNOWN"}
          isLoading={loading}
          delay={0}
          hint="Composite state across every monitored component"
        />
        <OpsKpi
          label="System Availability"
          icon={Activity}
          value={health.data?.availability ?? null}
          suffix="%"
          isLoading={loading}
          delay={0.03}
          hint="Uptime across the current reporting window"
        />
        <OpsKpi
          label="Monitored Services"
          icon={Network}
          value={health.data?.monitoredServices ?? null}
          isLoading={loading}
          delay={0.06}
          hint="Components reporting to the health collector"
        />
        <OpsKpi
          label="Open Incidents"
          icon={Siren}
          value={health.data?.openIncidents ?? null}
          isLoading={loading}
          delay={0.09}
          hint="Operational incidents currently unresolved"
        />
      </div>

      <OpsSection
        title="Service Status"
        description="Every platform component monitored by the operations collector."
        bodyClassName="p-4"
      >
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {services.map((service, index) => (
            <ServiceStatusCard
              key={service.id}
              service={service}
              icon={SERVICE_ICONS[service.id] ?? Cpu}
              delay={index * 0.02}
            />
          ))}
        </div>
      </OpsSection>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <OpsSection
          title="Operational Events"
          description="Availability changes, saturation warnings and recovery notices."
        >
          <AwaitingMonitoring detail="Operational events are emitted by the health collector and alerting engine. No events are simulated in this console." />
        </OpsSection>

        <OpsSection
          title="Capacity & Entitlement"
          description="Resource headroom and licensing posture."
        >
          <div className="space-y-2">
            <StatRow label="Storage Utilisation" hint="Capture evidence against quota" />
            <StatRow label="Compute Headroom" hint="CPU and memory available for inspection" />
            <StatRow label="License Seats" hint="Consumed against entitlement" />
            <StatRow label="Retention Window" hint="Days of captures and telemetry retained" />
            <StatRow label="Last Health Evaluation" hint="Most recent collector scrape" />
          </div>
        </OpsSection>
      </div>
    </div>
  );
}
