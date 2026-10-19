import { createFileRoute } from "@tanstack/react-router";
import { GitCommitHorizontal, Globe2, PackageCheck, Rocket, Undo2, Workflow } from "lucide-react";

import { Button } from "@/components/ui/button";
import { AwaitingMonitoring, OpsKpi, OpsSection, StatRow } from "@/components/observability";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService, OBSERVABILITY_WRITE_READY } from "@/services/observabilityService";

export const Route = createFileRoute("/ops/deployments")({
  head: () => ({
    meta: [
      { title: "Deployment Center · Velorix Sentinel" },
      { name: "description", content: "Release history, build metadata and rollback controls." },
      { property: "og:title", content: "Deployment Center · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Release history, build metadata and rollback controls.",
      },
    ],
  }),
  component: DeploymentCenterPage,
});

function DeploymentCenterPage() {
  const deployments = useObservabilityResource(observabilityService.listDeployments);
  const history = deployments.data ?? [];
  const current = history[0] ?? null;

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <OpsKpi
          label="Current Version"
          icon={PackageCheck}
          value={current?.version ?? null}
          hint="Release currently serving traffic"
        />
        <OpsKpi
          label="Build Number"
          icon={GitCommitHorizontal}
          value={current?.buildNumber ?? null}
          hint="CI build identifier for this release"
        />
        <OpsKpi
          label="Environment"
          icon={Globe2}
          value={current?.environment ?? null}
          hint="Target environment of the active release"
        />
        <OpsKpi
          label="Build Status"
          icon={Workflow}
          value={current?.state ?? null}
          hint="Outcome of the most recent pipeline run"
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <OpsSection
          title="Deployment History"
          description="Every release promoted to an environment, with commit and operator attribution."
        >
          {history.length ? (
            <ul className="space-y-2">
              {history.map((record) => (
                <li
                  key={record.id}
                  className="rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-mono text-xs">
                      {record.version} · build {record.buildNumber}
                    </span>
                    <span className="text-[11px] text-muted-foreground">
                      {new Date(record.deployedAt).toLocaleString()}
                    </span>
                  </div>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    {record.environment}
                    {record.region ? ` · ${record.region}` : ""}
                    {record.deployedBy ? ` · ${record.deployedBy}` : ""}
                  </p>
                  {record.releaseNotes && (
                    <p className="mt-1 text-[11px] text-muted-foreground">{record.releaseNotes}</p>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <AwaitingMonitoring
              icon={Rocket}
              detail="Deployment records are published by the release pipeline. No builds, commits or release notes are simulated here."
            />
          )}
        </OpsSection>

        <div className="space-y-4">
          <OpsSection
            title="Release Details"
            description="Attribution for the deployment currently in service."
          >
            <div className="space-y-2">
              <StatRow label="Commit SHA" value={current?.commitSha ?? undefined} />
              <StatRow label="Region" value={current?.region ?? undefined} />
              <StatRow label="Deployed By" value={current?.deployedBy ?? undefined} />
              <StatRow
                label="Deployed At"
                value={
                  current?.deployedAt ? new Date(current.deployedAt).toLocaleString() : undefined
                }
              />
            </div>
          </OpsSection>

          <OpsSection
            title="Rollback"
            description="Restore the previously known-good release image."
          >
            <div className="space-y-3">
              <p className="text-xs text-muted-foreground">
                Rollback re-deploys the prior container image. Database migrations are forward-only
                and are never reverted automatically.
              </p>
              <Button variant="outline" size="sm" disabled={!OBSERVABILITY_WRITE_READY}>
                <Undo2 className="size-3.5" aria-hidden="true" />
                Roll back release
              </Button>
              {!OBSERVABILITY_WRITE_READY && (
                <p className="text-[11px] text-muted-foreground">
                  Enabled once the deployment control API is available.
                </p>
              )}
            </div>
          </OpsSection>
        </div>
      </div>

      <OpsSection
        title="CI/CD Pipeline"
        description="Build, test, scan and deploy stages for frontend and backend."
      >
        <AwaitingMonitoring
          icon={Workflow}
          detail="Pipeline runs, stage durations and artifacts surface here once GitHub Actions reporting is connected."
        />
      </OpsSection>
    </div>
  );
}
