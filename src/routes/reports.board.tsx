import { createFileRoute } from "@tanstack/react-router";
import { Banknote, Building2, Gauge, ShieldCheck, Target } from "lucide-react";

import { AwaitingData } from "@/components/governance/AwaitingData";
import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { PostureGauge } from "@/components/governance/PostureGauge";
import { useGovernanceResource } from "@/hooks/useGovernanceResource";
import { governanceService } from "@/services/governanceService";
import type { BoardBriefing } from "@/types/governance";

export const Route = createFileRoute("/reports/board")({
  head: () => ({
    meta: [
      { title: "Board View · Velorix Sentinel" },
      {
        name: "description",
        content: "Board-level security posture summary for executive reporting.",
      },
      { property: "og:title", content: "Board View · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Board-level security posture summary for executive reporting.",
      },
    ],
  }),
  component: BoardView,
});

const MATURITY_LEVELS = [
  { level: 1, label: "Initial", detail: "Ad hoc, reactive security activity." },
  { level: 2, label: "Developing", detail: "Repeatable practices, limited documentation." },
  { level: 3, label: "Defined", detail: "Documented, consistently applied processes." },
  { level: 4, label: "Managed", detail: "Measured, reviewed and continuously improved." },
  { level: 5, label: "Optimising", detail: "Adaptive, quantitatively managed programme." },
];

function BoardView() {
  const briefing = useGovernanceResource<BoardBriefing>(
    (signal) => governanceService.getBoardBriefing(signal),
    [],
  );

  const data = briefing.status === "ready" ? briefing.data : null;

  return (
    <div className="space-y-6">
      <p className="text-xs text-muted-foreground">
        A simplified, non-technical view intended for board and executive committee reporting. Every
        figure below originates from the governance service — nothing is estimated.
      </p>

      <div className="grid gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
        <GovernanceSection
          title="Business Risk Level"
          description="Overall exposure in business terms"
          bodyClassName="flex justify-center py-6"
        >
          <PostureGauge
            score={data?.complianceReadiness ?? null}
            band={data?.businessRisk ?? "UNKNOWN"}
            label="Readiness"
          />
        </GovernanceSection>

        <div className="grid gap-4 sm:grid-cols-2">
          <GovernanceSection title="Security Investment" description="Budget utilisation">
            {data?.investmentUtilisation != null ? (
              <p className="font-mono text-2xl font-semibold">{data.investmentUtilisation}%</p>
            ) : (
              <AwaitingData compact icon={Banknote} detail="Finance integration not connected." />
            )}
          </GovernanceSection>

          <GovernanceSection title="Compliance Readiness" description="Across active frameworks">
            {data?.complianceReadiness != null ? (
              <p className="font-mono text-2xl font-semibold">{data.complianceReadiness}%</p>
            ) : (
              <AwaitingData compact icon={ShieldCheck} detail="No assessments recorded yet." />
            )}
          </GovernanceSection>

          <GovernanceSection title="Operational Status" description="Platform and SOC availability">
            <AwaitingData compact icon={Gauge} detail="Reported by the platform health service." />
          </GovernanceSection>

          <GovernanceSection title="Organisational Impact" description="Business units affected">
            <AwaitingData
              compact
              icon={Building2}
              detail="Requires asset and business-unit mapping."
            />
          </GovernanceSection>
        </div>
      </div>

      <GovernanceSection
        title="Security Maturity Level"
        description="Programme maturity on a five-level scale"
      >
        <ol className="grid gap-2 sm:grid-cols-5">
          {MATURITY_LEVELS.map((item) => {
            const active = data?.maturityLevel === item.level;
            return (
              <li
                key={item.level}
                className={
                  active
                    ? "rounded-xl border border-primary/40 bg-primary/10 p-3"
                    : "rounded-xl border border-border/70 bg-card/30 p-3"
                }
              >
                <p className="font-mono text-xs text-primary">Level {item.level}</p>
                <p className="mt-0.5 text-sm font-medium">{item.label}</p>
                <p className="mt-0.5 text-[11px] text-muted-foreground">{item.detail}</p>
              </li>
            );
          })}
        </ol>
        {data?.maturityLevel == null && (
          <p className="mt-3 text-[11px] font-medium text-muted-foreground">
            Awaiting Live Security Data — current maturity level not yet assessed.
          </p>
        )}
      </GovernanceSection>

      <GovernanceSection
        title="Strategic Recommendations"
        description="Actions requiring board awareness or decision"
      >
        {data?.recommendations?.length ? (
          <ul className="space-y-2">
            {data.recommendations.map((item) => (
              <li key={item} className="flex items-start gap-2 text-xs text-muted-foreground">
                <Target className="mt-0.5 size-3.5 shrink-0 text-primary" aria-hidden="true" />
                <span>{item}</span>
              </li>
            ))}
          </ul>
        ) : (
          <AwaitingData
            icon={Target}
            detail="Recommendations are generated from real posture, risk and compliance findings."
          />
        )}
      </GovernanceSection>
    </div>
  );
}
