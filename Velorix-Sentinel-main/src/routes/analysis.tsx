import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { RefreshCw, Radar } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import {
  AnalysisOverviewPanel,
  AnalysisRunList,
  PacketDetailSheet,
  PacketTable,
} from "@/components/analysis";
import { ProtectedRoute } from "@/components/auth";
import { EmptyState, ErrorState, PageHeader, RiskBadge, StatusBadge } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  useAnalysisDetail,
  useAnalysisList,
  useThreatSummary,
  usePackets,
} from "@/hooks/useAnalysis";
import { DashboardLayout } from "@/layouts";
import type { Packet, PacketQuery } from "@/types/analysis";
import { isRunning } from "@/types/analysis";
import { formatDuration } from "@/utils/analysisFormat";
import { formatDateTime, formatNumber } from "@/utils/format";
import { APP_NAME } from "@/utils/constants";

const DESCRIPTION = "Decoded frames and threat verdicts for every inspected capture.";

export const Route = createFileRoute("/analysis")({
  component: AnalysisPage,
  head: () => ({
    meta: [
      { title: `Packet analysis · ${APP_NAME}` },
      { name: "description", content: DESCRIPTION },
      { property: "og:title", content: `Packet analysis · ${APP_NAME}` },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function AnalysisPage() {
  return (
    <ProtectedRoute>
      <DashboardLayout>
        <AnalysisWorkspace />
      </DashboardLayout>
    </ProtectedRoute>
  );
}

function AnalysisWorkspace() {
  const navigate = useNavigate();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedPacket, setSelectedPacket] = useState<Packet | null>(null);
  const [packetQuery, setPacketQuery] = useState<PacketQuery>({
    page: 0,
    size: 50,
    sort: "packetNumber",
    direction: "ASC",
  });

  const runsQuery = useAnalysisList({ page: 0, size: 25, sort: "createdAt", direction: "DESC" });
  const runs = useMemo(() => runsQuery.data?.content ?? [], [runsQuery.data]);

  // Select the most recent run as soon as the listing resolves.
  useEffect(() => {
    if (!selectedId && runs.length > 0) {
      setSelectedId(runs[0].id);
    }
  }, [runs, selectedId]);

  const detail = useAnalysisDetail(selectedId);
  const live = detail.data ? isRunning(detail.data.analysis) : false;
  const summary = useThreatSummary(selectedId, live);
  const packets = usePackets(selectedId, packetQuery);

  const analysis = detail.data?.analysis;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Packet analysis"
        description={DESCRIPTION}
        actions={
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => void runsQuery.refetch()}>
              <RefreshCw className="size-4" aria-hidden="true" />
              Refresh
            </Button>
            <Button size="sm" onClick={() => void navigate({ to: "/upload" })}>
              Upload capture
            </Button>
          </div>
        }
      />

      <div className="grid gap-6 xl:grid-cols-[20rem_1fr]">
        <aside className="space-y-3">
          <h2 className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            Inspection runs
          </h2>
          {runsQuery.error ? (
            <ErrorState error={runsQuery.error} onRetry={() => void runsQuery.refetch()} />
          ) : (
            <AnalysisRunList
              runs={runs}
              selectedId={selectedId}
              isLoading={runsQuery.isLoading}
              onSelect={(run) => {
                setSelectedId(run.id);
                setSelectedPacket(null);
                setPacketQuery((current) => ({ ...current, page: 0 }));
              }}
            />
          )}
        </aside>

        <section className="min-w-0 space-y-5">
          {!selectedId ? (
            <EmptyState
              icon={Radar}
              title="Select an inspection run"
              description="Pick a capture on the left, or upload a new PCAP to start a fresh inspection."
              action={
                <Button size="sm" onClick={() => void navigate({ to: "/upload" })}>
                  Upload capture
                </Button>
              }
            />
          ) : detail.error ? (
            <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />
          ) : (
            <>
              {analysis && (
                <div className="rounded-2xl border border-border bg-surface/60 p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h2 className="truncate font-display text-lg font-semibold">
                        {analysis.originalFileName}
                      </h2>
                      <p className="mt-1 text-sm text-muted-foreground">
                        Started {formatDateTime(analysis.startedAt)} ·{" "}
                        {formatNumber(analysis.processedPackets)} of{" "}
                        {formatNumber(analysis.totalPackets)} frames stored · engine took{" "}
                        {formatDuration(analysis.duration)}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <StatusBadge status={analysis.status} />
                      <RiskBadge score={analysis.riskScore} />
                    </div>
                  </div>

                  {isRunning(analysis) && (
                    <div className="mt-4 space-y-1.5">
                      <Progress value={analysis.progressPercent} className="h-2" />
                      <p className="text-xs text-muted-foreground">
                        Pipeline stage: {analysis.stage.toLowerCase()} · {analysis.progressPercent}%
                      </p>
                    </div>
                  )}

                  {analysis.status === "FAILED" && analysis.failureReason && (
                    <p role="alert" className="mt-4 text-sm text-destructive">
                      {analysis.failureReason}
                    </p>
                  )}
                </div>
              )}

              <Tabs defaultValue="overview" className="space-y-4">
                <TabsList>
                  <TabsTrigger value="overview">Overview</TabsTrigger>
                  <TabsTrigger value="packets">Packets</TabsTrigger>
                  <TabsTrigger value="conversations">Conversations</TabsTrigger>
                </TabsList>

                <TabsContent value="overview">
                  <AnalysisOverviewPanel
                    analysis={analysis}
                    summary={summary.data}
                    isLoading={detail.isLoading || summary.isLoading}
                    error={summary.error}
                    onRetry={() => void summary.refetch()}
                  />
                </TabsContent>

                <TabsContent value="packets">
                  <PacketTable
                    packets={packets.data?.content ?? []}
                    totalElements={packets.data?.totalElements ?? 0}
                    totalPages={packets.data?.totalPages ?? 0}
                    query={packetQuery}
                    onQueryChange={(patch) =>
                      setPacketQuery((current) => ({ ...current, ...patch }))
                    }
                    isLoading={packets.isLoading}
                    isFetching={packets.isFetching}
                    error={packets.error}
                    onRetry={() => void packets.refetch()}
                    selectedId={selectedPacket?.id ?? null}
                    onSelect={setSelectedPacket}
                  />
                </TabsContent>

                <TabsContent value="conversations">
                  <EmptyState
                    icon={Radar}
                    title="Conversation analysis is not available yet"
                    description="Flow reconstruction will appear here once the backend exposes conversation aggregates."
                  />
                </TabsContent>
              </Tabs>
            </>
          )}
        </section>
      </div>

      {selectedId && (
        <PacketDetailSheet
          analysisId={selectedId}
          packet={selectedPacket}
          onOpenChange={(open) => {
            if (!open) setSelectedPacket(null);
          }}
        />
      )}
    </div>
  );
}
