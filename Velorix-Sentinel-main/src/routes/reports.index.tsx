import { createFileRoute } from "@tanstack/react-router";
import {
  Activity,
  AlertTriangle,
  ClipboardCheck,
  Gauge,
  ServerCog,
  ShieldCheck,
} from "lucide-react";

import { AwaitingData } from "@/components/governance/AwaitingData";
import { ExecutiveKpi } from "@/components/governance/ExecutiveKpi";
import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { PostureGauge } from "@/components/governance/PostureGauge";
import { useGovernanceResource } from "@/hooks/useGovernanceResource";
import { governanceService } from "@/services/governanceService";
import type { ExecutiveOverviewData } from "@/types/governance";

export const Route = createFileRoute("/reports/")({
  head: () => ({
    meta: [
      { title: "Executive Reporting · Velorix Sentinel" },
      {
        name: "description",
        content: "Executive security posture, risk and compliance reporting for leadership.",
      },
      { property: "og:title", content: "Executive Reporting · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Executive security posture, risk and compliance reporting for leadership.",
      },
    ],
  }),
  component: ExecutiveDashboard,
});

const HEALTH_ROWS = [
  {
    key: "detection",
    label: "Detection pipeline",
    detail: "Packet inspection and rule evaluation",
  },
  { key: "ingestion", label: "Capture ingestion", detail: "Upload, validation and storage" },
  { key: "intel", label: "Intelligence feeds", detail: "IOC, CVE and actor enrichment" },
  { key: "reporting", label: "Reporting services", detail: "Generation, scheduling and delivery" },
];

function ExecutiveDashboard() {
  const overview = useGovernanceResource<ExecutiveOverviewData>(
    (signal) => governanceService.getExecutiveOverview(signal),
    [],
  );

  const data = overview.status === "ready" ? overview.data : null;
  const isLoading = overview.status === "loading";

  return (
    <div className="space-y-6">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <ExecutiveKpi
            label="Security posture"
            icon={ShieldCheck}
            value={data?.postureScore ?? null}
            band={data ? "STRONG" : "UNKNOWN"}
            isLoading={isLoading}
            hint="Composite of detection, compliance and risk"
            delay={0}
          />
          <ExecutiveKpi
            label="Risk exposure"
            icon={AlertTriangle}
            value={data?.riskScore ?? null}
            isLoading={isLoading}
            hint="Residual risk across the register"
            delay={0.04}
          />
          <ExecutiveKpi
            label="Compliance readiness"
            icon={ClipboardCheck}
            value={data?.complianceScore ?? null}
            suffix="%"
            isLoading={isLoading}
            hint="Weighted across active frameworks"
            delay={0.08}
          />
          <ExecutiveKpi
            label="Critical assets"
            icon={ServerCog}
            value={data?.criticalAssets ?? null}
            isLoading={isLoading}
            hint="Assets flagged business-critical"
            delay={0.12}
          />
          <ExecutiveKpi
            label="Open incidents"
            icon={Activity}
            value={data?.openIncidents ?? null}
            isLoading={isLoading}
            hint="Cases currently under investigation"
            delay={0.16}
          />
          <ExecutiveKpi
            label="Resolved incidents"
            icon={Gauge}
            value={data?.resolvedIncidents ?? null}
            isLoading={isLoading}
            hint="Closed within the reporting period"
            delay={0.2}
          />
        </div>

        <GovernanceSection
          title="Security Score Card"
          description="Overall organisational posture"
          bodyClassName="flex flex-col items-center gap-4 py-6"
        >
          <PostureGauge
            score={data?.postureScore ?? null}
            band={data?.platformHealth ?? "UNKNOWN"}
          />
          <p className="max-w-[16rem] text-center text-[11px] text-muted-foreground">
            {data?.summary ??
              "The posture score is calculated by the governance service from detection coverage, control assessments and open risk. No provisional value is displayed."}
          </p>
        </GovernanceSection>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <GovernanceSection
          title="Threat Landscape Summary"
          description="Attack surface, active campaigns and detection efficacy"
        >
          <AwaitingData
            detail="Threat landscape roll-ups appear once the governance service aggregates detection results from analysed captures."
            icon={AlertTriangle}
          />
        </GovernanceSection>

        <GovernanceSection
          title="Compliance Status Overview"
          description="Weighted posture across active frameworks"
        >
          <AwaitingData
            detail="Framework coverage is published here after the first control assessment is recorded."
            icon={ClipboardCheck}
          />
        </GovernanceSection>
      </div>

      <GovernanceSection
        title="System Health Indicators"
        description="Operational status of the platform services that feed executive reporting"
      >
        <ul className="divide-y divide-border/60">
          {HEALTH_ROWS.map((row) => (
            <li key={row.key} className="flex items-center justify-between gap-4 py-3">
              <div className="min-w-0">
                <p className="text-sm font-medium">{row.label}</p>
                <p className="text-xs text-muted-foreground">{row.detail}</p>
              </div>
              <span className="shrink-0 rounded-full border border-border bg-muted/30 px-2.5 py-0.5 text-[10px] font-medium tracking-wide text-muted-foreground uppercase">
                Awaiting live data
              </span>
            </li>
          ))}
        </ul>
      </GovernanceSection>

      {overview.status === "error" && (
        <p className="text-xs text-primary" role="alert">
          Executive overview unavailable: {overview.error}
        </p>
      )}
    </div>
  );
}
