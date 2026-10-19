import { createFileRoute } from "@tanstack/react-router";
import {
  Boxes,
  GitPullRequestArrow,
  KeyRound,
  PackageSearch,
  ScanSearch,
  ServerCog,
  Sparkles,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { AwaitingMonitoring, OpsSection, PipelineBadge, StatRow } from "@/components/observability";
import { DEVSECOPS_CHECKS } from "@/data/observability-reference";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService } from "@/services/observabilityService";
import type { DevSecOpsCheck } from "@/types/observability";

export const Route = createFileRoute("/ops/devsecops")({
  head: () => ({
    meta: [
      { title: "DevSecOps Center · Velorix Sentinel" },
      {
        name: "description",
        content: "Pipeline security gates, SAST, dependency and container scanning status.",
      },
      { property: "og:title", content: "DevSecOps Center · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Pipeline security gates, SAST, dependency and container scanning status.",
      },
    ],
  }),
  component: DevSecOpsPage,
});

const CATEGORY_ICON: Record<DevSecOpsCheck["category"], LucideIcon> = {
  PIPELINE: GitPullRequestArrow,
  SAST: ScanSearch,
  QUALITY: Sparkles,
  DEPENDENCY: PackageSearch,
  CONTAINER: Boxes,
  SECRETS: KeyRound,
  IAC: ServerCog,
};

function DevSecOpsPage() {
  const checks = useObservabilityResource(observabilityService.listDevSecOpsChecks);
  const rows = checks.data?.length ? checks.data : DEVSECOPS_CHECKS;

  return (
    <div className="space-y-6">
      <OpsSection
        title="Pipeline & Security Gates"
        description="Build, quality and security controls applied to every change before release."
      >
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {rows.map((check) => {
            const Icon = CATEGORY_ICON[check.category];
            return (
              <article
                key={check.id}
                className="rounded-2xl border border-border/70 bg-muted/10 p-4 transition-colors hover:border-primary/30"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 items-start gap-3">
                    <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
                      <Icon className="size-4" aria-hidden="true" />
                    </span>
                    <div className="min-w-0">
                      <h3 className="truncate text-sm font-semibold">{check.name}</h3>
                      <p className="mt-0.5 text-[11px] text-muted-foreground">{check.provider}</p>
                    </div>
                  </div>
                  <PipelineBadge state={check.state} />
                </div>
                <p className="mt-3 text-[11px] text-muted-foreground">{check.detail}</p>
                <div className="mt-3 flex items-center justify-between border-t border-border/60 pt-2.5 text-[11px]">
                  <span className="text-muted-foreground">Findings</span>
                  <span className="font-mono">
                    {check.findings ?? "Awaiting Live Platform Monitoring"}
                  </span>
                </div>
              </article>
            );
          })}
        </div>
      </OpsSection>

      <div className="grid gap-4 xl:grid-cols-2">
        <OpsSection
          title="Infrastructure Overview"
          description="Where the platform runs and how it is provisioned."
        >
          <div className="space-y-2">
            <StatRow label="Frontend Hosting" hint="Static asset delivery via edge CDN" />
            <StatRow label="Backend Runtime" hint="Containerised Spring Boot service" />
            <StatRow label="Database Hosting" hint="Managed PostgreSQL instance" />
            <StatRow label="Object Storage" hint="Capture evidence bucket" />
            <StatRow label="Secrets Backend" hint="Managed secret store, never in source" />
          </div>
        </OpsSection>

        <OpsSection
          title="Pipeline Runs"
          description="Recent workflow executions with stage outcomes and durations."
        >
          <AwaitingMonitoring detail="Workflow runs surface here once GitHub Actions, SonarQube and Snyk reporting is connected to the platform. No pipeline results are simulated." />
        </OpsSection>
      </div>
    </div>
  );
}
