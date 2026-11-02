import { createFileRoute, Outlet } from "@tanstack/react-router";
import { FileBarChart, Info } from "lucide-react";

import { ModulePlaceholder, PageHeader } from "@/components/common";
import { GovernanceTabs } from "@/components/governance/GovernanceTabs";
import { GOVERNANCE_BACKEND_READY } from "@/services/governanceService";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/reports")({
  component: GovernanceLayout,
  head: () => ({
    meta: [
      { title: `Executive Reporting & Compliance · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Executive security reporting, compliance posture, risk register and audit readiness for CISOs, security managers and auditors.",
      },
      { property: "og:title", content: `Executive Reporting & Compliance · ${APP_NAME}` },
      {
        property: "og:description",
        content:
          "Board-ready security reporting, compliance frameworks, risk management and audit evidence in one governance centre.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function GovernanceLayout() {
  if (!GOVERNANCE_BACKEND_READY) {
    return (
      <ModulePlaceholder
        title="Executive Reporting & Compliance"
        description="Governance, risk and compliance reporting for executives, security managers and auditors."
        icon={FileBarChart}
        plannedFeatures={[
          "Board-ready executive security summaries & risk postures",
          "Compliance framework mapping (ISO 27001, SOC 2, NIST CSF)",
          "Automated scheduled PDF/HTML report exports",
          "Enterprise risk registers & mitigation tracking",
        ]}
      />
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Executive Reporting & Compliance"
        description="Governance, risk and compliance reporting for executives, security managers and auditors."
      />

      {!GOVERNANCE_BACKEND_READY && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-xl border border-primary/25 bg-primary/5 px-4 py-3"
        >
          <Info className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
          <p className="text-xs text-muted-foreground">
            <span className="font-medium text-foreground">
              Governance services are not connected yet.
            </span>{" "}
            Every metric, score and finding on these pages is sourced exclusively from the
            governance backend. Sections show{" "}
            <span className="font-medium text-foreground">“Awaiting Live Security Data”</span>{" "}
            rather than sample or estimated figures. Framework structures and knowledge articles are
            published reference material.
          </p>
        </div>
      )}

      <GovernanceTabs />

      <Outlet />
    </div>
  );
}
