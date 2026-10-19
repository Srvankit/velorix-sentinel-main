import { createFileRoute } from "@tanstack/react-router";
import { CalendarClock, FileBarChart, FileText, Repeat } from "lucide-react";

import { AwaitingData } from "@/components/governance/AwaitingData";
import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { useGovernanceResource } from "@/hooks/useGovernanceResource";
import { governanceService } from "@/services/governanceService";
import type { ReportSummary, ScheduledReport } from "@/types/governance";

export const Route = createFileRoute("/reports/library")({
  head: () => ({
    meta: [
      { title: "Report Library · Velorix Sentinel" },
      {
        name: "description",
        content: "Catalogue of generated executive, compliance and operational reports.",
      },
      { property: "og:title", content: "Report Library · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Catalogue of generated executive, compliance and operational reports.",
      },
    ],
  }),
  component: ReportsCenter,
});

const REPORT_TYPES = [
  {
    key: "EXECUTIVE_SUMMARY",
    title: "Executive Security Summary",
    detail: "Posture, exposure and decisions required, written for a non-technical audience.",
  },
  {
    key: "INCIDENT_SUMMARY",
    title: "Incident Summary Report",
    detail: "Volume, severity mix, response times and notable cases for the period.",
  },
  {
    key: "THREAT_INTELLIGENCE",
    title: "Threat Intelligence Report",
    detail: "Observed indicators, adversary techniques and relevance to the estate.",
  },
  {
    key: "COMPLIANCE",
    title: "Compliance Status Report",
    detail: "Control coverage, gaps and remediation progress per framework.",
  },
  {
    key: "RISK_ASSESSMENT",
    title: "Risk Assessment Report",
    detail: "Inherent versus residual exposure with treatment plans and owners.",
  },
  {
    key: "ASSET_SECURITY",
    title: "Asset Security Report",
    detail: "Critical asset inventory, exposure and monitoring coverage.",
  },
  {
    key: "SYSTEM_HEALTH",
    title: "System Health Report",
    detail: "Availability and performance of detection and ingestion services.",
  },
  {
    key: "AUDIT",
    title: "Audit Readiness Report",
    detail: "Evidence completeness, open findings and review status.",
  },
];

function ReportsCenter() {
  const reports = useGovernanceResource<ReportSummary[]>(
    (signal) => governanceService.listReports(signal),
    [],
  );
  const schedules = useGovernanceResource<ScheduledReport[]>(
    (signal) => governanceService.listSchedules(signal),
    [],
  );

  const generated = reports.status === "ready" ? reports.data : [];
  const scheduled = schedules.status === "ready" ? schedules.data : [];

  return (
    <div className="space-y-6">
      <GovernanceSection
        title="Report Catalogue"
        description="Report types this platform produces once governance services are connected"
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {REPORT_TYPES.map((report) => (
            <article
              key={report.key}
              className="flex flex-col gap-2 rounded-xl border border-border/70 bg-card/30 p-3"
            >
              <span className="grid size-8 place-items-center rounded-lg bg-primary/10 text-primary">
                <FileText className="size-4" aria-hidden="true" />
              </span>
              <h3 className="text-sm font-medium">{report.title}</h3>
              <p className="text-xs text-muted-foreground">{report.detail}</p>
              <span className="mt-auto pt-1 text-[10px] tracking-wide text-muted-foreground uppercase">
                Awaiting live data
              </span>
            </article>
          ))}
        </div>
      </GovernanceSection>

      <div className="grid gap-4 lg:grid-cols-2">
        <GovernanceSection
          title="Generated Reports"
          description="Documents produced from real analysis results"
        >
          {generated.length === 0 ? (
            <AwaitingData
              icon={FileBarChart}
              title="Awaiting Live Security Data"
              detail="No reports have been generated. Reports become available after captures are analysed and the governance service is connected."
            />
          ) : (
            <ul className="divide-y divide-border/60">
              {generated.map((report) => (
                <li key={report.id} className="flex items-center justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{report.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {report.period ?? "Period unspecified"} · {report.owner ?? "Unassigned"}
                    </p>
                  </div>
                  <span className="shrink-0 text-[10px] tracking-wide text-muted-foreground uppercase">
                    {report.status}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </GovernanceSection>

        <GovernanceSection
          title="Scheduled Reporting"
          description="Recurring delivery to executives, auditors and stakeholders"
          actions={
            <span className="inline-flex items-center gap-1.5 text-[11px] text-muted-foreground">
              <Repeat className="size-3.5" aria-hidden="true" />
              Daily · Weekly · Monthly · Quarterly
            </span>
          }
        >
          {scheduled.length === 0 ? (
            <AwaitingData
              icon={CalendarClock}
              title="Awaiting Live Security Data"
              detail="Schedules are configured against generated report definitions. None exist until the reporting service is live."
            />
          ) : (
            <ul className="divide-y divide-border/60">
              {scheduled.map((schedule) => (
                <li key={schedule.id} className="flex items-center justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{schedule.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {schedule.cadence} · {schedule.delivery}
                    </p>
                  </div>
                  <span className="shrink-0 text-[10px] tracking-wide text-muted-foreground uppercase">
                    {schedule.enabled ? "Active" : "Paused"}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </GovernanceSection>
      </div>
    </div>
  );
}
