import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { ArrowLeft, CalendarRange, Landmark, Radar } from "lucide-react";

import { AwaitingData } from "@/components/governance/AwaitingData";
import { ControlChecklist } from "@/components/governance/ControlChecklist";
import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { FRAMEWORK_BY_SLUG, type FrameworkDefinition } from "@/data/compliance-frameworks";
import { useGovernanceResource } from "@/hooks/useGovernanceResource";
import { governanceService } from "@/services/governanceService";
import type { FrameworkAssessment } from "@/types/governance";

export const Route = createFileRoute("/reports/compliance/$framework")({
  head: () => ({
    meta: [
      { title: "Compliance Framework · Velorix Sentinel" },
      {
        name: "description",
        content: "Control-by-control compliance status for the selected framework.",
      },
      { property: "og:title", content: "Compliance Framework · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Control-by-control compliance status for the selected framework.",
      },
    ],
  }),
  loader: ({ params }) => {
    const framework = FRAMEWORK_BY_SLUG.get(params.framework);
    if (!framework) throw notFound();
    return { framework };
  },
  component: FrameworkDetail,
  notFoundComponent: FrameworkMissing,
});

function FrameworkMissing() {
  return (
    <GovernanceSection title="Framework not found" description="This framework is not tracked.">
      <Link to="/reports/compliance" className="text-xs text-primary hover:underline">
        Back to compliance frameworks
      </Link>
    </GovernanceSection>
  );
}

function FrameworkDetail() {
  const { framework } = Route.useLoaderData() as { framework: FrameworkDefinition };
  const assessment = useGovernanceResource<FrameworkAssessment>(
    (signal) => governanceService.getFrameworkAssessment(framework.id, signal),
    [framework.id],
  );

  const data = assessment.status === "ready" ? assessment.data : null;

  return (
    <div className="space-y-6">
      <Link
        to="/reports/compliance"
        className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-3.5" aria-hidden="true" />
        All frameworks
      </Link>

      <GovernanceSection
        title={framework.name}
        description={framework.focus}
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted/30 px-2.5 py-1 text-[10px] tracking-wide text-muted-foreground uppercase">
            <Landmark className="size-3" aria-hidden="true" />
            {framework.authority}
          </span>
        }
      >
        <p className="text-xs leading-relaxed text-muted-foreground">{framework.overview}</p>
        <p className="mt-3 inline-flex items-center gap-1.5 text-[11px] text-muted-foreground">
          <CalendarRange className="size-3.5" aria-hidden="true" />
          {framework.auditCycle}
        </p>
      </GovernanceSection>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <GovernanceSection
          title="Control Structure"
          description="Published control groups joined with recorded assessment state"
          bodyClassName="pt-0"
        >
          <ControlChecklist
            requirements={framework.requirements}
            assessments={data?.controls ?? []}
          />
        </GovernanceSection>

        <div className="space-y-4">
          <GovernanceSection title="Assessment Summary" description="Reported by the audit service">
            {data ? (
              <dl className="space-y-2 text-xs">
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Coverage</dt>
                  <dd className="font-mono font-medium">{data.coveragePercent ?? "—"}%</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Controls met</dt>
                  <dd className="font-mono font-medium">{data.metControls ?? "—"}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Partial</dt>
                  <dd className="font-mono font-medium">{data.partialControls ?? "—"}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Gaps</dt>
                  <dd className="font-mono font-medium">{data.gapControls ?? "—"}</dd>
                </div>
              </dl>
            ) : (
              <AwaitingData compact detail="No assessment has been recorded for this framework." />
            )}
          </GovernanceSection>

          <GovernanceSection
            title="Platform Evidence Sources"
            description="Controls this platform can evidence with live telemetry"
          >
            <ul className="space-y-2">
              {framework.platformSupport.map((item) => (
                <li key={item} className="flex items-start gap-2 text-xs text-muted-foreground">
                  <Radar className="mt-0.5 size-3.5 shrink-0 text-primary" aria-hidden="true" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </GovernanceSection>
        </div>
      </div>
    </div>
  );
}
