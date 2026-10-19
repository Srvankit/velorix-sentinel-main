import { useMemo } from "react";

import { CategoryBarChart, ChartCard, DistributionDonutChart } from "@/components/charts";
import { humaniseEnum } from "@/utils/format";
import type { Threat, ThreatStats } from "@/types/threats";

interface AnalyticsProps {
  stats: ThreatStats | null | undefined;
  threats: Threat[];
  isLoading: boolean;
}

function toSeries(record: Record<string, number> | null | undefined, humanise = false) {
  return Object.entries(record ?? {})
    .filter(([, count]) => Number.isFinite(count))
    .map(([label, count]) => ({ label: humanise ? humaniseEnum(label) : label, count }))
    .sort((a, b) => b.count - a.count);
}

/** Charted breakdowns, built only from values the backend returned. */
export function ThreatAnalytics({ stats, threats, isLoading }: AnalyticsProps) {
  const severity = useMemo(
    () => toSeries(stats?.severityCounts as Record<string, number> | null),
    [stats],
  );
  const categories = useMemo(
    () => toSeries(stats?.categoryCounts as Record<string, number> | null, true).slice(0, 8),
    [stats],
  );
  const sources = useMemo(
    () => (stats?.topSources ?? []).slice(0, 8).map((row) => ({ label: row.ip, count: row.count })),
    [stats],
  );
  const destinations = useMemo(
    () =>
      (stats?.topDestinations ?? [])
        .slice(0, 8)
        .map((row) => ({ label: row.ip, count: row.count })),
    [stats],
  );

  const protocols = useMemo(() => {
    const counts = new Map<string, number>();
    for (const threat of threats) {
      if (!threat.protocol) continue;
      counts.set(threat.protocol, (counts.get(threat.protocol) ?? 0) + 1);
    }
    return [...counts.entries()]
      .map(([label, count]) => ({ label, count }))
      .sort((a, b) => b.count - a.count);
  }, [threats]);

  const hourly = useMemo(() => {
    const buckets = new Array<number>(24).fill(0);
    let seen = false;
    for (const threat of threats) {
      if (!threat.detectedAt) continue;
      const date = new Date(threat.detectedAt);
      if (Number.isNaN(date.getTime())) continue;
      buckets[date.getHours()] += 1;
      seen = true;
    }
    if (!seen) return [];
    return buckets.map((count, hour) => ({
      label: `${String(hour).padStart(2, "0")}:00`,
      count,
    }));
  }, [threats]);

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <ChartCard
        title="Severity distribution"
        description="Findings grouped by impact ranking."
        isLoading={isLoading}
        isEmpty={severity.length === 0}
        emptyTitle="No threats detected yet"
      >
        <DistributionDonutChart data={severity} />
      </ChartCard>

      <ChartCard
        title="Threat distribution"
        description="Detection categories raised across your captures."
        isLoading={isLoading}
        isEmpty={categories.length === 0}
        emptyTitle="No threats detected yet"
      >
        <CategoryBarChart data={categories} />
      </ChartCard>

      <ChartCard
        title="Top source addresses"
        description="Hosts most frequently attributed as the origin."
        isLoading={isLoading}
        isEmpty={sources.length === 0}
        emptyTitle="No source telemetry yet"
      >
        <CategoryBarChart data={sources} />
      </ChartCard>

      <ChartCard
        title="Top destination addresses"
        description="Hosts most frequently targeted."
        isLoading={isLoading}
        isEmpty={destinations.length === 0}
        emptyTitle="No destination telemetry yet"
      >
        <CategoryBarChart data={destinations} />
      </ChartCard>

      <ChartCard
        title="Protocol distribution"
        description="Protocols carrying the detected findings."
        isLoading={isLoading}
        isEmpty={protocols.length === 0}
        emptyTitle="No protocol data yet"
      >
        <CategoryBarChart data={protocols} layout="horizontal" />
      </ChartCard>

      <ChartCard
        title="Hourly activity"
        description="Detections by hour of day across the loaded feed."
        isLoading={isLoading}
        isEmpty={hourly.length === 0}
        emptyTitle="No detection activity yet"
      >
        <CategoryBarChart data={hourly} layout="horizontal" />
      </ChartCard>
    </div>
  );
}
