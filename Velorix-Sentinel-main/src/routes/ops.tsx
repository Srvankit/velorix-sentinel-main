import { createFileRoute, Outlet } from "@tanstack/react-router";
import { Info } from "lucide-react";

import { ProtectedRoute } from "@/components/auth";
import { OpsTabs } from "@/components/observability";
import { ErrorBoundary, PageHeader } from "@/components/common";
import { DashboardLayout } from "@/layouts";
import { OBSERVABILITY_BACKEND_READY } from "@/services/observabilityService";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/ops")({
  component: ObservabilityLayout,
  head: () => ({
    meta: [
      { title: `Platform Observability · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Monitor platform health, metrics, logs, APIs, database, deployments, backups and DevSecOps posture from one operations console.",
      },
      { property: "og:title", content: `Platform Observability · ${APP_NAME}` },
      {
        property: "og:description",
        content:
          "Operations, telemetry, log exploration, deployment history and DevSecOps controls for the Velorix Sentinel platform.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function ObservabilityLayout() {
  return (
    <ProtectedRoute roles={["ADMIN"]}>
      <DashboardLayout>
        <div className="space-y-6">
          <PageHeader
            title="Platform Observability & DevSecOps Center"
            description="Health, telemetry, logs, deployments, resilience and pipeline security for the Velorix Sentinel platform."
          />

          {!OBSERVABILITY_BACKEND_READY && (
            <div
              role="status"
              className="flex items-start gap-3 rounded-xl border border-primary/25 bg-primary/5 px-4 py-3"
            >
              <Info className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
              <p className="text-xs text-muted-foreground">
                <span className="font-medium text-foreground">
                  Monitoring services are not connected yet.
                </span>{" "}
                Health, metrics, logs, latency and deployment records come exclusively from the
                observability backend. Sections show{" "}
                <span className="font-medium text-foreground">
                  “Awaiting Live Platform Monitoring”
                </span>{" "}
                rather than sample telemetry. Service, metric and pipeline catalogues below are
                shipped platform reference material.
              </p>
            </div>
          )}

          <OpsTabs />

          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </div>
      </DashboardLayout>
    </ProtectedRoute>
  );
}
