import { Link, Outlet, createFileRoute } from "@tanstack/react-router";

import { ProtectedRoute } from "@/components/auth";
import { DashboardLayout } from "@/layouts";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/intel")({
  component: IntelLayout,
  head: () => ({
    meta: [
      { title: `Threat Intelligence Center · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Enterprise threat intelligence: IOC management, MITRE ATT&CK coverage, CVE tracking, malware knowledge base, watchlists and threat reporting.",
      },
      { property: "og:title", content: `Threat Intelligence Center · ${APP_NAME}` },
      {
        property: "og:description",
        content:
          "IOCs, ATT&CK coverage, CVEs, malware families, watchlists and reports in one workspace.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

const TABS = [
  { to: "/intel", label: "Overview", exact: true },
  { to: "/intel/iocs", label: "IOCs" },
  { to: "/intel/mitre", label: "MITRE ATT&CK" },
  { to: "/intel/cve", label: "CVEs" },
  { to: "/intel/malware", label: "Malware" },
  { to: "/intel/feed", label: "Threat Feed" },
  { to: "/intel/map", label: "Threat Map" },
  { to: "/intel/watchlists", label: "Watchlists" },
  { to: "/intel/reports", label: "Reports" },
  { to: "/intel/library", label: "Library" },
] as const;

function IntelLayout() {
  return (
    <ProtectedRoute>
      <DashboardLayout>
        <nav
          aria-label="Threat intelligence sections"
          className="mb-6 -mx-1 flex gap-1 overflow-x-auto pb-1"
        >
          {TABS.map((tab) => (
            <Link
              key={tab.to}
              to={tab.to}
              activeOptions={{ exact: "exact" in tab ? tab.exact : false }}
              activeProps={{
                className:
                  "focus-ring shrink-0 rounded-full border border-primary/40 bg-primary/15 px-3.5 py-1.5 text-xs font-medium text-primary",
              }}
              inactiveProps={{
                className:
                  "focus-ring shrink-0 rounded-full border border-border px-3.5 py-1.5 text-xs text-muted-foreground transition-colors hover:border-primary/30 hover:text-foreground",
              }}
            >
              {tab.label}
            </Link>
          ))}
        </nav>
        <Outlet />
      </DashboardLayout>
    </ProtectedRoute>
  );
}
