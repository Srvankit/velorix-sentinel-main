import { createFileRoute } from "@tanstack/react-router";
import { Download, FileBarChart, FileText } from "lucide-react";
import { toast } from "sonner";

import { AwaitingBackend, IncidentCard, IncidentTable } from "@/components/incidents";
import { PageHeader } from "@/components/common";
import { Button } from "@/components/ui/button";
import { useIncidentResource } from "@/hooks/useIncidentResource";
import { incidentService } from "@/services/incidentService";

export const Route = createFileRoute("/incidents/reports")({
  head: () => ({
    meta: [
      { title: "Incident Reports · Velorix Sentinel" },
      {
        name: "description",
        content: "Post-incident reporting and lessons-learned documentation.",
      },
      { property: "og:title", content: "Incident Reports · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Post-incident reporting and lessons-learned documentation.",
      },
    ],
  }),
  component: IncidentReportsPage,
});

const UNAVAILABLE = "Available after backend integration.";

const SECTIONS = [
  {
    title: "Executive Summary",
    detail: "Impact, scope and business risk written for leadership.",
  },
  {
    title: "Technical Details",
    detail: "Attack chain, affected assets, indicators and packet-level findings.",
  },
  {
    title: "Response Actions",
    detail: "Containment, eradication and recovery steps with timestamps.",
  },
  {
    title: "Recommendations",
    detail: "Hardening and detection improvements to prevent recurrence.",
  },
  {
    title: "Lessons Learned",
    detail: "Post-incident review outcomes and follow-up owners.",
  },
];

function IncidentReportsPage() {
  const incidents = useIncidentResource((signal) => incidentService.list({ size: 50 }, signal));

  return (
    <div className="space-y-6">
      <PageHeader
        title="Incident Reports"
        description="Generate structured post-incident reports for closed and active cases."
        actions={
          <Button
            size="sm"
            disabled
            title={UNAVAILABLE}
            onClick={() => toast.info("Export report", { description: UNAVAILABLE })}
          >
            <Download className="size-4" aria-hidden="true" />
            Export
          </Button>
        }
      />

      <IncidentCard
        title="Report Structure"
        description="Every generated report contains these sections"
        icon={FileText}
      >
        <ol className="grid gap-2 sm:grid-cols-2">
          {SECTIONS.map((section, index) => (
            <li key={section.title} className="rounded-xl border border-border/70 bg-card/40 p-3">
              <p className="text-sm font-medium">
                <span className="mr-2 font-mono text-xs text-primary">
                  {String(index + 1).padStart(2, "0")}
                </span>
                {section.title}
              </p>
              <p className="mt-1 text-[11px] text-muted-foreground">{section.detail}</p>
            </li>
          ))}
        </ol>
      </IncidentCard>

      <IncidentCard
        title="Reportable Cases"
        description="Select a case to build its post-incident report"
        icon={FileBarChart}
      >
        <IncidentTable
          incidents={incidents.data?.content ?? []}
          isLoading={incidents.status === "loading"}
          awaiting={incidents.status === "awaiting"}
          pageSize={8}
          emptyTitle="No cases available for reporting."
          emptyDetail="Reports are generated from real incidents once the response backend is connected."
        />
      </IncidentCard>

      <AwaitingBackend
        compact
        icon={Download}
        title="Report generation and export are disabled."
        detail="PDF and CSV export will be enabled with the reporting endpoints. No sample reports are produced."
      />
    </div>
  );
}
