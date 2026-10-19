import { createFileRoute } from "@tanstack/react-router";
import { ClipboardCheck, FileCheck2, ShieldCheck } from "lucide-react";

import { AwaitingData } from "@/components/governance/AwaitingData";
import { FrameworkCard } from "@/components/governance/FrameworkCard";
import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { FRAMEWORKS } from "@/data/compliance-frameworks";
import { useGovernanceResource } from "@/hooks/useGovernanceResource";
import { governanceService } from "@/services/governanceService";
import type { FrameworkAssessment } from "@/types/governance";

export const Route = createFileRoute("/reports/compliance/")({
  head: () => ({
    meta: [
      { title: "Compliance Center · Velorix Sentinel" },
      {
        name: "description",
        content: "ISO 27001, SOC 2, NIST CSF, PCI DSS and GDPR control coverage.",
      },
      { property: "og:title", content: "Compliance Center · Velorix Sentinel" },
      {
        property: "og:description",
        content: "ISO 27001, SOC 2, NIST CSF, PCI DSS and GDPR control coverage.",
      },
    ],
  }),
  component: ComplianceCenter,
});

function ComplianceCenter() {
  const assessments = useGovernanceResource<FrameworkAssessment[]>(
    (signal) => governanceService.listComplianceAssessments(signal),
    [],
  );

  const byFramework = new Map(
    (assessments.status === "ready" ? assessments.data : []).map((item) => [item.framework, item]),
  );

  return (
    <div className="space-y-6">
      <GovernanceSection
        title="Compliance Frameworks"
        description="Published control structures tracked by this platform. Select a framework for its control breakdown."
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {FRAMEWORKS.map((framework) => (
            <FrameworkCard
              key={framework.id}
              framework={framework}
              assessment={byFramework.get(framework.id) ?? null}
            />
          ))}
        </div>
      </GovernanceSection>

      <div className="grid gap-4 lg:grid-cols-2">
        <GovernanceSection
          title="Compliance Score Overview"
          description="Weighted readiness across all active frameworks"
        >
          <AwaitingData
            icon={ShieldCheck}
            detail="Scores are computed from recorded control assessments. Until assessments exist, no readiness percentage is shown."
          />
        </GovernanceSection>

        <GovernanceSection
          title="Evidence Collection Status"
          description="Artefacts gathered against required controls"
        >
          <AwaitingData
            icon={FileCheck2}
            detail="Evidence counts are reported by the audit service once artefacts are linked to controls."
          />
        </GovernanceSection>
      </div>

      <GovernanceSection
        title="Gap Analysis & Remediation Tracking"
        description="Unmet controls, owners and remediation progress"
      >
        <AwaitingData
          icon={ClipboardCheck}
          detail="Gap analysis requires at least one completed framework assessment. Nothing is inferred from framework structure alone."
        />
      </GovernanceSection>
    </div>
  );
}
