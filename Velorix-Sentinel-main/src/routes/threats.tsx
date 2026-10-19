import { createFileRoute } from "@tanstack/react-router";
import { RefreshCw, ShieldAlert } from "lucide-react";
import { lazy, Suspense, useMemo, useState } from "react";

import { ErrorState, SkeletonChart } from "@/components/common";
import { PageHeader } from "@/components/common";
import {
  DetectionRuleGrid,
  MitreCoverage,
  RecommendationPanel,
  RiskAssessmentPanel,
  ThreatDetailDrawer,
  ThreatOverviewCards,
  ThreatTimelinePanel,
  ThreatsTable,
} from "@/components/threats";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useDetectionRules, useThreatStats, useThreats } from "@/hooks/useThreats";
import type { Threat } from "@/types/threats";
import { APP_NAME } from "@/utils/constants";

const ThreatAnalytics = lazy(() =>
  import("@/components/threats/ThreatAnalytics").then((module) => ({
    default: module.ThreatAnalytics,
  })),
);

export const Route = createFileRoute("/threats")({
  component: ThreatsPage,
  head: () => ({
    meta: [
      { title: "Threats · " + APP_NAME },
      {
        name: "description",
        content: "Triage every detection finding with severity, evidence and MITRE mapping.",
      },
      { property: "og:title", content: "Threats · " + APP_NAME },
      {
        property: "og:description",
        content: "Triage every detection finding with severity, evidence and MITRE mapping.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

const FEED_QUERY = { page: 0, size: 200, sort: "detectedAt", direction: "DESC" } as const;

function ThreatsPage() {
  const statsQuery = useThreatStats();
  const feedQuery = useThreats(FEED_QUERY);
  const rulesQuery = useDetectionRules();

  const [selected, setSelected] = useState<Threat | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const threats = useMemo(() => feedQuery.data?.content ?? [], [feedQuery.data]);
  const rules = useMemo(() => rulesQuery.data ?? [], [rulesQuery.data]);

  const refreshing = statsQuery.isFetching || feedQuery.isFetching || rulesQuery.isFetching;

  const refresh = () => {
    void statsQuery.refetch();
    void feedQuery.refetch();
    void rulesQuery.refetch();
  };

  const openThreat = (threat: Threat) => {
    setSelected(threat);
    setDrawerOpen(true);
  };

  return (
    <div className="space-y-8">
      <PageHeader
        title="Threat detection"
        description="Findings raised by the detection engine across every inspected capture."
        actions={
          <Button variant="outline" size="sm" onClick={refresh} disabled={refreshing}>
            <RefreshCw
              className={refreshing ? "size-4 animate-spin" : "size-4"}
              aria-hidden="true"
            />
            Refresh
          </Button>
        }
      />

      {statsQuery.isError && !statsQuery.data ? (
        <ErrorState
          title="Threat statistics unavailable"
          error={statsQuery.error}
          onRetry={() => void statsQuery.refetch()}
        />
      ) : (
        <ThreatOverviewCards stats={statsQuery.data} isLoading={statsQuery.isLoading} />
      )}

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <ThreatTimelinePanel
            buckets={statsQuery.data?.timeline}
            isLoading={statsQuery.isLoading}
            error={statsQuery.error ?? null}
            onRetry={() => void statsQuery.refetch()}
          />
        </div>
        <RiskAssessmentPanel
          stats={statsQuery.data}
          threats={threats}
          isLoading={statsQuery.isLoading}
        />
      </div>

      <Tabs defaultValue="findings" className="space-y-6">
        <TabsList className="flex-wrap">
          <TabsTrigger value="findings">Findings</TabsTrigger>
          <TabsTrigger value="analytics">Analytics</TabsTrigger>
          <TabsTrigger value="mitre">MITRE ATT&amp;CK</TabsTrigger>
          <TabsTrigger value="rules">Detection rules</TabsTrigger>
        </TabsList>

        <TabsContent value="findings" className="space-y-6">
          {feedQuery.isError && !feedQuery.data ? (
            <ErrorState
              title="Threat feed unavailable"
              error={feedQuery.error}
              onRetry={() => void feedQuery.refetch()}
            />
          ) : (
            <div className="grid gap-4 xl:grid-cols-3">
              <div className="xl:col-span-2">
                <ThreatsTable
                  threats={threats}
                  isLoading={feedQuery.isLoading}
                  onSelect={openThreat}
                />
              </div>
              <RecommendationPanel
                stats={statsQuery.data}
                threats={threats}
                isLoading={feedQuery.isLoading || statsQuery.isLoading}
              />
            </div>
          )}
        </TabsContent>

        <TabsContent value="analytics">
          <Suspense fallback={<SkeletonChart />}>
            <ThreatAnalytics
              stats={statsQuery.data}
              threats={threats}
              isLoading={statsQuery.isLoading}
            />
          </Suspense>
        </TabsContent>

        <TabsContent value="mitre">
          <MitreCoverage
            threats={threats}
            rules={rules}
            isLoading={feedQuery.isLoading || rulesQuery.isLoading}
          />
        </TabsContent>

        <TabsContent value="rules">
          {rulesQuery.isError && !rulesQuery.data ? (
            <ErrorState
              title="Detection rules unavailable"
              error={rulesQuery.error}
              onRetry={() => void rulesQuery.refetch()}
            />
          ) : (
            <DetectionRuleGrid rules={rules} threats={threats} isLoading={rulesQuery.isLoading} />
          )}
        </TabsContent>
      </Tabs>

      <ThreatDetailDrawer
        threat={selected}
        open={drawerOpen}
        onOpenChange={(open) => {
          setDrawerOpen(open);
          if (!open) setSelected(null);
        }}
      />

      <span className="sr-only" aria-live="polite">
        {refreshing ? "Loading threat data" : `${threats.length} threats loaded`}
      </span>
      <ShieldAlert className="hidden" aria-hidden="true" />
    </div>
  );
}
