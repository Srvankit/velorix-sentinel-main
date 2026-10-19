import { createFileRoute } from "@tanstack/react-router";
import { ListChecks, ServerCog, ShieldAlert, Workflow } from "lucide-react";

import { AwaitingData } from "@/components/governance/AwaitingData";
import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { RiskMatrix } from "@/components/governance/RiskMatrix";
import { useGovernanceResource } from "@/hooks/useGovernanceResource";
import { governanceService } from "@/services/governanceService";
import type { RiskOverview } from "@/types/governance";

export const Route = createFileRoute("/reports/risk")({
  head: () => ({
    meta: [
      { title: "Risk Management · Velorix Sentinel" },
      { name: "description", content: "Risk register, heat matrix and treatment tracking." },
      { property: "og:title", content: "Risk Management · Velorix Sentinel" },
      { property: "og:description", content: "Risk register, heat matrix and treatment tracking." },
    ],
  }),
  component: RiskCenter,
});

const TREATMENTS = [
  { key: "MITIGATE", label: "Mitigate", detail: "Reduce likelihood or impact through controls." },
  { key: "TRANSFER", label: "Transfer", detail: "Shift exposure via insurance or contract." },
  { key: "ACCEPT", label: "Accept", detail: "Formally accept with a named owner and expiry." },
  { key: "AVOID", label: "Avoid", detail: "Discontinue the activity generating the risk." },
];

function RiskCenter() {
  const overview = useGovernanceResource<RiskOverview>(
    (signal) => governanceService.getRiskOverview(signal),
    [],
  );

  const data = overview.status === "ready" ? overview.data : null;
  const register = data?.register ?? [];

  return (
    <div className="space-y-6">
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
        <GovernanceSection
          title="Risk Heat Map"
          description="Likelihood against impact across the enterprise risk register"
        >
          <RiskMatrix entries={register} />
        </GovernanceSection>

        <div className="space-y-4">
          <GovernanceSection title="Residual Exposure" description="After control effectiveness">
            {data ? (
              <dl className="space-y-2 text-xs">
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Average residual score</dt>
                  <dd className="font-mono font-medium">{data.residualAverage ?? "—"}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Critical assets at risk</dt>
                  <dd className="font-mono font-medium">{data.criticalAssets ?? "—"}</dd>
                </div>
              </dl>
            ) : (
              <AwaitingData compact detail="Residual scoring requires a populated risk register." />
            )}
          </GovernanceSection>

          <GovernanceSection
            title="Treatment Options"
            description="Every register entry must carry one"
          >
            <ul className="space-y-2.5">
              {TREATMENTS.map((option) => (
                <li key={option.key} className="flex items-start gap-2">
                  <Workflow className="mt-0.5 size-3.5 shrink-0 text-primary" aria-hidden="true" />
                  <div>
                    <p className="text-xs font-medium">{option.label}</p>
                    <p className="text-[11px] text-muted-foreground">{option.detail}</p>
                  </div>
                </li>
              ))}
            </ul>
          </GovernanceSection>
        </div>
      </div>

      <GovernanceSection
        title="Risk Register"
        description="Identified risks with owner, treatment and review date"
      >
        {register.length === 0 ? (
          <AwaitingData
            icon={ListChecks}
            detail="The register is populated by the governance service. No sample or illustrative risks are shown."
          />
        ) : (
          <ul className="divide-y divide-border/60">
            {register.map((entry) => (
              <li key={entry.id} className="flex items-center justify-between gap-3 py-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">
                    <span className="mr-2 font-mono text-xs text-primary">{entry.reference}</span>
                    {entry.title}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {entry.category} · {entry.owner ?? "Unassigned"} · {entry.treatment}
                  </p>
                </div>
                <span className="shrink-0 font-mono text-xs">{entry.residualScore ?? "—"}</span>
              </li>
            ))}
          </ul>
        )}
      </GovernanceSection>

      <div className="grid gap-4 lg:grid-cols-2">
        <GovernanceSection
          title="Risk by Category"
          description="Distribution across operational, technical and third-party risk"
        >
          <AwaitingData icon={ShieldAlert} detail="Category breakdown follows the risk register." />
        </GovernanceSection>
        <GovernanceSection
          title="Asset Risk Mapping"
          description="Exposure attributed to critical business assets"
        >
          <AwaitingData
            icon={ServerCog}
            detail="Asset mapping requires the asset inventory service and a populated register."
          />
        </GovernanceSection>
      </div>
    </div>
  );
}
