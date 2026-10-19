import { createFileRoute } from "@tanstack/react-router";
import { CloudUpload, FileSpreadsheet, FileText, Mail, Presentation, Table2 } from "lucide-react";
import { toast } from "sonner";

import { AwaitingData } from "@/components/governance/AwaitingData";
import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { Button } from "@/components/ui/button";
import { GOVERNANCE_EXPORT_READY } from "@/services/governanceService";

export const Route = createFileRoute("/reports/exports")({
  head: () => ({
    meta: [
      { title: "Export Center · Velorix Sentinel" },
      {
        name: "description",
        content: "Generate and download reporting exports for auditors and stakeholders.",
      },
      { property: "og:title", content: "Export Center · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Generate and download reporting exports for auditors and stakeholders.",
      },
    ],
  }),
  component: ExportCenter,
});

const DESTINATIONS = [
  {
    key: "PDF",
    label: "PDF export",
    detail: "Branded, print-ready executive document.",
    icon: FileText,
  },
  {
    key: "EXCEL",
    label: "Excel export",
    detail: "Workbook with per-section data sheets.",
    icon: FileSpreadsheet,
  },
  {
    key: "CSV",
    label: "CSV export",
    detail: "Raw tabular data for downstream tooling.",
    icon: Table2,
  },
  {
    key: "POWERPOINT",
    label: "PowerPoint export",
    detail: "Board-ready slide deck.",
    icon: Presentation,
  },
  {
    key: "EMAIL",
    label: "Email delivery",
    detail: "Scheduled distribution to stakeholders.",
    icon: Mail,
  },
  {
    key: "CLOUD",
    label: "Cloud storage",
    detail: "Archive to a connected storage bucket.",
    icon: CloudUpload,
  },
];

function ExportCenter() {
  function handleExport(label: string) {
    if (!GOVERNANCE_EXPORT_READY) {
      toast.info(`${label} is not available yet`, {
        description: "Export services are connected alongside the governance backend.",
      });
      return;
    }
    toast.success(`${label} queued`);
  }

  return (
    <div className="space-y-6">
      <GovernanceSection
        title="Export Destinations"
        description="Formats and delivery channels supported by the reporting service"
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {DESTINATIONS.map((destination) => {
            const Icon = destination.icon;
            return (
              <article
                key={destination.key}
                className="flex flex-col gap-2 rounded-xl border border-border/70 bg-card/30 p-3"
              >
                <span className="grid size-8 place-items-center rounded-lg bg-primary/10 text-primary">
                  <Icon className="size-4" aria-hidden="true" />
                </span>
                <h3 className="text-sm font-medium">{destination.label}</h3>
                <p className="text-xs text-muted-foreground">{destination.detail}</p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="mt-auto"
                  onClick={() => handleExport(destination.label)}
                >
                  Export
                </Button>
              </article>
            );
          })}
        </div>
      </GovernanceSection>

      <GovernanceSection
        title="Export History"
        description="Previously generated and delivered documents"
      >
        <AwaitingData
          icon={CloudUpload}
          detail="Export jobs are recorded once the reporting service produces its first document."
        />
      </GovernanceSection>
    </div>
  );
}
