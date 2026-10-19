import { createFileRoute } from "@tanstack/react-router";
import { FileCheck2, FolderSearch, History, ScrollText } from "lucide-react";

import { AwaitingData } from "@/components/governance/AwaitingData";
import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { Progress } from "@/components/ui/progress";
import { useGovernanceResource } from "@/hooks/useGovernanceResource";
import { governanceService } from "@/services/governanceService";
import type { AuditOverview } from "@/types/governance";

export const Route = createFileRoute("/reports/audit")({
  head: () => ({
    meta: [
      { title: "Audit Center · Velorix Sentinel" },
      {
        name: "description",
        content: "Audit evidence collection and reviewer workflow for assessments.",
      },
      { property: "og:title", content: "Audit Center · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Audit evidence collection and reviewer workflow for assessments.",
      },
    ],
  }),
  component: AuditCenter,
});

function AuditCenter() {
  const overview = useGovernanceResource<AuditOverview>(
    (signal) => governanceService.getAuditOverview(signal),
    [],
  );

  const data = overview.status === "ready" ? overview.data : null;
  const timeline = data?.timeline ?? [];
  const findings = data?.findings ?? [];
  const collected = data?.evidenceCollected ?? null;
  const required = data?.evidenceRequired ?? null;
  const evidencePercent =
    collected !== null && required !== null && required > 0
      ? Math.round((collected / required) * 100)
      : null;

  return (
    <div className="space-y-6">
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <GovernanceSection
          title="Audit Trail"
          description="Chronological record of security and governance activity"
        >
          {timeline.length === 0 ? (
            <AwaitingData
              icon={History}
              detail="The audit trail is written by backend services as security events, assessments and approvals occur."
            />
          ) : (
            <ol className="relative space-y-4 border-l border-border/60 pl-4">
              {timeline.map((event) => (
                <li key={event.id} className="relative">
                  <span
                    className="absolute top-1.5 -left-[21px] size-2 rounded-full bg-primary"
                    aria-hidden="true"
                  />
                  <p className="text-sm font-medium">{event.label}</p>
                  {event.detail && <p className="text-xs text-muted-foreground">{event.detail}</p>}
                  <p className="mt-0.5 text-[11px] text-muted-foreground">
                    {new Date(event.occurredAt).toLocaleString()} · {event.actor ?? "System"}
                  </p>
                </li>
              ))}
            </ol>
          )}
        </GovernanceSection>

        <div className="space-y-4">
          <GovernanceSection
            title="Evidence Collection"
            description="Artefacts gathered for the current audit cycle"
          >
            {evidencePercent !== null ? (
              <div className="space-y-2">
                <div className="flex items-baseline justify-between">
                  <span className="text-xs text-muted-foreground">
                    {collected} of {required} artefacts
                  </span>
                  <span className="font-mono text-sm font-semibold">{evidencePercent}%</span>
                </div>
                <Progress value={evidencePercent} className="h-1.5" />
              </div>
            ) : (
              <AwaitingData
                compact
                icon={FileCheck2}
                detail="Evidence requirements are defined by the active framework assessments."
              />
            )}
          </GovernanceSection>

          <GovernanceSection
            title="Review Status"
            description="Pending sign-off from control owners"
          >
            <AwaitingData
              compact
              icon={FolderSearch}
              detail="Review queues populate when assessments are submitted for approval."
            />
          </GovernanceSection>
        </div>
      </div>

      <GovernanceSection
        title="Audit Findings"
        description="Open observations, owners and remediation deadlines"
      >
        {findings.length === 0 ? (
          <AwaitingData
            icon={ScrollText}
            detail="Findings are recorded by internal or external auditors. None are fabricated for demonstration."
          />
        ) : (
          <ul className="divide-y divide-border/60">
            {findings.map((finding) => (
              <li key={finding.id} className="flex items-center justify-between gap-3 py-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">
                    <span className="mr-2 font-mono text-xs text-primary">{finding.reference}</span>
                    {finding.title}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {finding.framework ?? "General"} · {finding.owner ?? "Unassigned"}
                  </p>
                </div>
                <span className="shrink-0 text-[10px] tracking-wide text-muted-foreground uppercase">
                  {finding.severity} · {finding.status}
                </span>
              </li>
            ))}
          </ul>
        )}
      </GovernanceSection>
    </div>
  );
}
