import { Link, Outlet, createFileRoute } from "@tanstack/react-router";

import { ProtectedRoute } from "@/components/auth";
import { ErrorBoundary } from "@/components/common";
import { DashboardLayout } from "@/layouts";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/incidents")({
  component: IncidentsLayout,
  head: () => ({
    meta: [
      { title: `Incident Response & Case Management · ${APP_NAME}` },
      {
        name: "description",
        content:
          "SOC incident response: incident queue, case management, investigation timelines, evidence chain of custody, playbooks and reporting.",
      },
      { property: "og:title", content: `Incident Response · ${APP_NAME}` },
      {
        property: "og:description",
        content:
          "Incident queue, case workflow, evidence handling, response playbooks and incident reporting for security analysts.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

const TABS = [
  { to: "/incidents", label: "Incident Center", exact: true },
  { to: "/incidents/list", label: "Incidents" },
  { to: "/incidents/workspace", label: "My Workspace" },
  { to: "/incidents/evidence", label: "Evidence" },
  { to: "/incidents/playbooks", label: "Playbooks" },
  { to: "/incidents/reports", label: "Reports" },
] as const;

function IncidentsLayout() {
  return (
    <ProtectedRoute>
      <DashboardLayout>
        <nav
          aria-label="Incident response sections"
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
        <ErrorBoundary>
          <Outlet />
        </ErrorBoundary>
      </DashboardLayout>
    </ProtectedRoute>
  );
}
