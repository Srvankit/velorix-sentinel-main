import { createFileRoute } from "@tanstack/react-router";
import { Download, FileBarChart } from "lucide-react";

import { EmptyState, PageHeader } from "@/components/common";
import { AwaitingFeed, IntelCard } from "@/components/intel";
import { Button } from "@/components/ui/button";
import type { ThreatReport } from "@/types/intel";
import { threatIntelService } from "@/services/threatIntelService";
import { useIntelResource } from "@/hooks/useIntelResource";

export const Route = createFileRoute("/intel/reports")({
  head: () => ({
    meta: [
      { title: "Intelligence Reports · Velorix Sentinel" },
      {
        name: "description",
        content: "Curated threat intelligence reporting and analyst assessments.",
      },
      { property: "og:title", content: "Intelligence Reports · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Curated threat intelligence reporting and analyst assessments.",
      },
    ],
  }),
  component: ThreatReportsPage,
});

const REPORT_SECTIONS = [
  "Executive Summary",
  "Technical Summary",
  "Indicators",
  "Timeline",
  "Recommendations",
];

function ThreatReportsPage() {
  const state = useIntelResource((signal) => threatIntelService.listReports(signal));
  const reports: ThreatReport[] = state.status === "ready" ? state.data : [];

  return (
    <>
      <PageHeader
        title="Threat Reports"
        description="Analyst-ready intelligence reporting with executive and technical narratives."
        actions={
          <Button variant="outline" size="sm" disabled title="Export unlocks with backend support">
            <Download className="size-3.5" aria-hidden="true" /> Export
          </Button>
        }
      />

      <IntelCard
        className="mt-6"
        title="Report structure"
        description="Every generated report follows this fixed template."
        icon={FileBarChart}
      >
        <ol className="grid gap-2 sm:grid-cols-5">
          {REPORT_SECTIONS.map((section, index) => (
            <li
              key={section}
              className="rounded-xl border border-border/70 bg-card/40 px-3 py-3 text-xs"
            >
              <span className="font-mono text-[11px] text-primary">0{index + 1}</span>
              <p className="mt-1 font-medium">{section}</p>
            </li>
          ))}
        </ol>
        <AwaitingFeed
          className="mt-4"
          detail="Reports generate once the intelligence backend is connected."
        />
      </IntelCard>

      {reports.length === 0 ? (
        <EmptyState
          className="mt-6"
          icon={FileBarChart}
          title="No reports published"
          description="Awaiting Live Threat Intelligence — generated reports will appear here with export controls enabled."
        />
      ) : (
        <div className="mt-6 grid gap-4 lg:grid-cols-2">
          {reports.map((report) => (
            <IntelCard
              key={report.id}
              title={report.title}
              description={new Date(report.publishedAt).toLocaleString()}
              icon={FileBarChart}
            >
              <div className="space-y-3 text-xs text-muted-foreground">
                <p>{report.executiveSummary}</p>
                <p>{report.technicalSummary}</p>
              </div>
            </IntelCard>
          ))}
        </div>
      )}
    </>
  );
}
