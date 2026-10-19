import { useMemo } from "react";

import { ChartCard, TimeSeriesAreaChart } from "@/components/charts";
import { SEVERITY_COLORS } from "@/components/charts";
import type { ApiError } from "@/types/api";
import type { ThreatTimelineBucket } from "@/types/threats";

interface TimelineProps {
  buckets: ThreatTimelineBucket[] | null | undefined;
  isLoading: boolean;
  error?: ApiError | null;
  onRetry?: () => void;
}

/** Detection activity over time, split by severity. */
export function ThreatTimelinePanel({ buckets, isLoading, error, onRetry }: TimelineProps) {
  const data = useMemo(
    () =>
      (buckets ?? []).map((bucket) => ({
        bucketStart: bucket.bucketStart,
        critical: bucket.critical ?? 0,
        high: bucket.high ?? 0,
        medium: bucket.medium ?? 0,
        low: bucket.low ?? 0,
      })),
    [buckets],
  );

  return (
    <ChartCard
      title="Threat timeline"
      description="Findings detected over time, grouped by severity."
      isLoading={isLoading}
      error={error ? { message: error.message } : null}
      onRetry={onRetry}
      isEmpty={data.length === 0}
      emptyTitle="No threats detected yet"
      emptyDescription="Run detection on a completed inspection to populate the timeline."
    >
      <TimeSeriesAreaChart
        data={data}
        xKey="bucketStart"
        height={260}
        series={[
          { key: "critical", label: "Critical", color: SEVERITY_COLORS.CRITICAL },
          { key: "high", label: "High", color: SEVERITY_COLORS.HIGH },
          { key: "medium", label: "Medium", color: SEVERITY_COLORS.MEDIUM },
          { key: "low", label: "Low", color: SEVERITY_COLORS.LOW },
        ]}
      />
    </ChartCard>
  );
}
