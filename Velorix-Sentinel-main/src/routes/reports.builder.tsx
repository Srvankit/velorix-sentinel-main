import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Hammer, Lock } from "lucide-react";
import { toast } from "sonner";

import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FRAMEWORKS } from "@/data/compliance-frameworks";
import { GOVERNANCE_EXPORT_READY } from "@/services/governanceService";
import type { ExportFormat } from "@/types/governance";

export const Route = createFileRoute("/reports/builder")({
  head: () => ({
    meta: [
      { title: "Report Builder · Velorix Sentinel" },
      {
        name: "description",
        content: "Compose custom executive and compliance reports from platform data.",
      },
      { property: "og:title", content: "Report Builder · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Compose custom executive and compliance reports from platform data.",
      },
    ],
  }),
  component: ReportBuilder,
});

const SECTIONS = [
  "Executive summary",
  "Threat landscape",
  "Incident review",
  "Compliance status",
  "Risk register",
  "Asset exposure",
  "Recommendations",
  "Appendix and evidence",
];

const DEPARTMENTS = ["Corporate IT", "Engineering", "Finance", "Operations", "Customer Support"];
const RISK_LEVELS = ["Critical", "High", "Medium", "Low"];

const FORMATS: { value: ExportFormat; label: string }[] = [
  { value: "PDF", label: "PDF document" },
  { value: "EXCEL", label: "Excel workbook" },
  { value: "CSV", label: "CSV data" },
  { value: "POWERPOINT", label: "PowerPoint deck" },
];

function toggle(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

function ReportBuilder() {
  const [title, setTitle] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [format, setFormat] = useState<ExportFormat>("PDF");
  const [sections, setSections] = useState<string[]>([SECTIONS[0]]);
  const [departments, setDepartments] = useState<string[]>([]);
  const [frameworks, setFrameworks] = useState<string[]>([]);
  const [riskLevels, setRiskLevels] = useState<string[]>([]);

  function handleGenerate() {
    if (!GOVERNANCE_EXPORT_READY) {
      toast.info("Report generation is not available yet", {
        description:
          "The reporting service is not connected. Your configuration is preserved but no document is produced.",
      });
      return;
    }
    toast.success("Report queued for generation");
  }

  return (
    <form
      className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]"
      onSubmit={(event) => {
        event.preventDefault();
        handleGenerate();
      }}
    >
      <div className="space-y-4">
        <GovernanceSection
          title="Report Definition"
          description="Name the report and set the reporting period"
        >
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <Label htmlFor="report-title">Report title</Label>
              <Input
                id="report-title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder="Q3 Executive Security Review"
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="report-from">Period start</Label>
              <Input
                id="report-from"
                type="date"
                value={from}
                onChange={(event) => setFrom(event.target.value)}
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="report-to">Period end</Label>
              <Input
                id="report-to"
                type="date"
                value={to}
                onChange={(event) => setTo(event.target.value)}
                className="mt-1.5"
              />
            </div>
          </div>
        </GovernanceSection>

        <GovernanceSection title="Sections" description="Choose the content blocks to include">
          <div className="grid gap-2 sm:grid-cols-2">
            {SECTIONS.map((section) => (
              <label key={section} className="flex items-center gap-2 text-xs">
                <Checkbox
                  checked={sections.includes(section)}
                  onCheckedChange={() => setSections((prev) => toggle(prev, section))}
                />
                {section}
              </label>
            ))}
          </div>
        </GovernanceSection>

        <GovernanceSection title="Filters" description="Scope the data included in the report">
          <div className="space-y-4">
            <fieldset>
              <legend className="mb-2 text-[11px] tracking-wide text-muted-foreground uppercase">
                Departments
              </legend>
              <div className="flex flex-wrap gap-2">
                {DEPARTMENTS.map((department) => (
                  <label key={department} className="flex items-center gap-2 text-xs">
                    <Checkbox
                      checked={departments.includes(department)}
                      onCheckedChange={() => setDepartments((prev) => toggle(prev, department))}
                    />
                    {department}
                  </label>
                ))}
              </div>
            </fieldset>

            <fieldset>
              <legend className="mb-2 text-[11px] tracking-wide text-muted-foreground uppercase">
                Frameworks
              </legend>
              <div className="flex flex-wrap gap-2">
                {FRAMEWORKS.map((framework) => (
                  <label key={framework.id} className="flex items-center gap-2 text-xs">
                    <Checkbox
                      checked={frameworks.includes(framework.id)}
                      onCheckedChange={() => setFrameworks((prev) => toggle(prev, framework.id))}
                    />
                    {framework.shortName}
                  </label>
                ))}
              </div>
            </fieldset>

            <fieldset>
              <legend className="mb-2 text-[11px] tracking-wide text-muted-foreground uppercase">
                Risk levels
              </legend>
              <div className="flex flex-wrap gap-2">
                {RISK_LEVELS.map((level) => (
                  <label key={level} className="flex items-center gap-2 text-xs">
                    <Checkbox
                      checked={riskLevels.includes(level)}
                      onCheckedChange={() => setRiskLevels((prev) => toggle(prev, level))}
                    />
                    {level}
                  </label>
                ))}
              </div>
            </fieldset>
          </div>
        </GovernanceSection>
      </div>

      <div className="space-y-4">
        <GovernanceSection title="Output" description="Delivery format for the generated document">
          <Label htmlFor="report-format" className="sr-only">
            Format
          </Label>
          <Select value={format} onValueChange={(value) => setFormat(value as ExportFormat)}>
            <SelectTrigger id="report-format">
              <SelectValue placeholder="Select a format" />
            </SelectTrigger>
            <SelectContent>
              {FORMATS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Button type="submit" className="mt-3 w-full gap-2">
            <Hammer className="size-4" aria-hidden="true" />
            Generate report
          </Button>

          {!GOVERNANCE_EXPORT_READY && (
            <p className="mt-2 flex items-start gap-1.5 text-[11px] text-muted-foreground">
              <Lock className="mt-0.5 size-3 shrink-0" aria-hidden="true" />
              Generation is disabled until the reporting service is connected. No placeholder
              document is produced.
            </p>
          )}
        </GovernanceSection>

        <GovernanceSection title="Live Preview" description="Rendered from real data only">
          <div className="rounded-xl border border-dashed border-border bg-muted/10 p-4">
            <p className="text-sm font-semibold">{title || "Untitled report"}</p>
            <p className="mt-0.5 text-[11px] text-muted-foreground">
              {from && to ? `${from} → ${to}` : "Reporting period not set"}
            </p>
            <ul className="mt-3 space-y-1">
              {sections.length === 0 ? (
                <li className="text-[11px] text-muted-foreground">No sections selected</li>
              ) : (
                sections.map((section) => (
                  <li key={section} className="flex justify-between text-[11px]">
                    <span>{section}</span>
                    <span className="text-muted-foreground">Awaiting live data</span>
                  </li>
                ))
              )}
            </ul>
          </div>
        </GovernanceSection>
      </div>
    </form>
  );
}
