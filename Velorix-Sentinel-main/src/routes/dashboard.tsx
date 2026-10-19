import { createFileRoute } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { Suspense, lazy } from "react";

import { ProtectedRoute } from "@/components/auth";
import { ErrorBoundary, PageHeader, SkeletonChart } from "@/components/common";
import { RecentAnalysesTable, RecentThreatsTable } from "@/components/dashboard";
import {
  ExecutiveOverview,
  LiveThreatFeed,
  PlatformStatus,
  QuickActions,
  RecentActivityPanel,
  SecurityKpiGrid,
  SecurityTimeline,
  SystemHealthPanel,
  UserProfilePanel,
} from "@/components/soc";
import { Button } from "@/components/ui/button";
import { dashboardKeys } from "@/hooks/useDashboard";
import { DashboardLayout } from "@/layouts";
import { APP_NAME } from "@/utils/constants";

/** Recharts is heavy — keep it out of the initial dashboard bundle. */
const DashboardCharts = lazy(() =>
  import("@/components/dashboard/DashboardCharts").then((m) => ({ default: m.DashboardCharts })),
);

export const Route = createFileRoute("/dashboard")({
  component: DashboardPage,
  head: () => ({
    meta: [
      { title: `Security operations center · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Enterprise SOC dashboard: executive risk posture, live threat feed, system health, platform status and analyst quick actions.",
      },
      { property: "og:title", content: `Security operations center · ${APP_NAME}` },
      {
        property: "og:description",
        content:
          "Monitor risk posture, detections, platform health and inspection activity from one enterprise console.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function DashboardPage() {
  const queryClient = useQueryClient();

  return (
    <ProtectedRoute>
      <DashboardLayout>
        <PageHeader
          title="Security operations center"
          description="Executive posture, live detections and platform health in a single console."
          actions={
            <Button
              variant="outline"
              size="sm"
              onClick={() => void queryClient.invalidateQueries({ queryKey: dashboardKeys.all })}
            >
              <RefreshCw className="size-4" aria-hidden="true" />
              Refresh
            </Button>
          }
        />

        <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="min-w-0 space-y-6">
            <ErrorBoundary title="Executive overview unavailable">
              <ExecutiveOverview />
            </ErrorBoundary>

            <ErrorBoundary title="Security metrics unavailable">
              <SecurityKpiGrid />
            </ErrorBoundary>

            <ErrorBoundary title="Charts unavailable">
              <Suspense
                fallback={
                  <div className="grid gap-4 lg:grid-cols-2">
                    <SkeletonChart />
                    <SkeletonChart />
                  </div>
                }
              >
                <DashboardCharts />
              </Suspense>
            </ErrorBoundary>

            <div className="grid gap-6 2xl:grid-cols-2">
              <ErrorBoundary title="Threat feed unavailable">
                <LiveThreatFeed />
              </ErrorBoundary>
              <ErrorBoundary title="Timeline unavailable">
                <SecurityTimeline />
              </ErrorBoundary>
            </div>

            <ErrorBoundary title="Investigations unavailable">
              <RecentAnalysesTable />
            </ErrorBoundary>

            <ErrorBoundary title="Detections unavailable">
              <RecentThreatsTable />
            </ErrorBoundary>

            <ErrorBoundary title="Activity unavailable">
              <RecentActivityPanel />
            </ErrorBoundary>
          </div>

          <aside className="min-w-0 space-y-6 xl:sticky xl:top-24 xl:self-start">
            <ErrorBoundary title="Profile unavailable">
              <UserProfilePanel />
            </ErrorBoundary>
            <ErrorBoundary title="Health unavailable">
              <SystemHealthPanel />
            </ErrorBoundary>
            <ErrorBoundary title="Quick actions unavailable">
              <QuickActions />
            </ErrorBoundary>
            <ErrorBoundary title="Platform status unavailable">
              <PlatformStatus />
            </ErrorBoundary>
          </aside>
        </div>
      </DashboardLayout>
    </ProtectedRoute>
  );
}
