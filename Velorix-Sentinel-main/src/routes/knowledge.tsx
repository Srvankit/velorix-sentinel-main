import { Outlet, createFileRoute } from "@tanstack/react-router";

import { ProtectedRoute } from "@/components/auth";
import { DashboardLayout } from "@/layouts";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/knowledge")({
  component: KnowledgeLayout,
  head: () => ({
    meta: [
      { title: `Security Knowledge Center · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Curated analyst reference library covering MITRE ATT&CK, OWASP Top 10, CVE triage, threat hunting, IOCs, malware families and network protocols.",
      },
      { property: "og:title", content: `Security Knowledge Center · ${APP_NAME}` },
      {
        property: "og:description",
        content: "Reference material for SOC analysts, written for practitioners.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function KnowledgeLayout() {
  return (
    <ProtectedRoute>
      <DashboardLayout>
        <Outlet />
      </DashboardLayout>
    </ProtectedRoute>
  );
}
