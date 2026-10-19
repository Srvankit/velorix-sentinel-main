import { Activity, Gauge, Globe, Layers, ShieldAlert, Timer } from "lucide-react";

import { StatTile } from "./StatTile";
import { ErrorState, SkeletonStat } from "@/components/common";
import type { Analysis, AnalysisThreatSummary } from "@/types/analysis";
import { formatDuration } from "@/utils/analysisFormat";
import { formatBytes, formatDateTime, formatNumber } from "@/utils/format";

interface AnalysisOverviewPanelProps {
  analysis: Analysis | undefined;
  summary: AnalysisThreatSummary | null | undefined;
  isLoading: boolean;
  error: unknown;
  onRetry: () => void;
}

/**
 * Overview of one inspection run built from `GET /analysis/{id}` and
 * `GET /analysis/{id}/summary` — the only aggregate endpoints the backend exposes.
 */
export function AnalysisOverviewPanel({
  analysis,
  summary,
  isLoading,
  error,
  onRetry,
}: AnalysisOverviewPanelProps) {
  if (error) {
    return <ErrorState error={error} onRetry={onRetry} />;
  }

  if (isLoading || !analysis) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 8 }).map((_, index) => (
          <SkeletonStat key={index} />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile
          label="Total packets"
          value={formatNumber(analysis.totalPackets)}
          hint={`${formatNumber(analysis.processedPackets)} stored · ${formatNumber(analysis.malformedPackets)} malformed`}
          icon={Layers}
        />
        <StatTile
          label="Average frame size"
          value={formatBytes(analysis.averagePacketSize)}
          hint={analysis.truncated ? "Capture was truncated" : "Across stored frames"}
          icon={Activity}
        />
        <StatTile
          label="Capture duration"
          value={formatDuration(analysis.captureDuration)}
          hint={
            analysis.captureStartedAt
              ? `From ${formatDateTime(analysis.captureStartedAt)}`
              : "Timestamps unavailable"
          }
          icon={Timer}
        />
        <StatTile
          label="Processing time"
          value={formatDuration(analysis.duration)}
          hint="Wall clock spent inspecting"
          icon={Gauge}
        />
        <StatTile
          label="Unique addresses"
          value={formatNumber(analysis.uniqueSourceIps + analysis.uniqueDestinationIps)}
          hint={`${formatNumber(analysis.uniqueSourceIps)} sources · ${formatNumber(analysis.uniqueDestinationIps)} destinations`}
          icon={Globe}
        />
        <StatTile
          label="Malicious frames"
          value={formatNumber(analysis.maliciousPackets)}
          hint={`${formatNumber(analysis.safePackets)} clean frames`}
          icon={ShieldAlert}
        />
        <StatTile
          label="Risk score"
          value={`${summary?.riskScore ?? analysis.riskScore}/100`}
          hint={summary?.overallStatus ? summary.overallStatus.toLowerCase() : "Awaiting verdict"}
          icon={Gauge}
        />
        <StatTile
          label="Threats detected"
          value={formatNumber(summary?.totalThreats ?? 0)}
          hint={
            summary
              ? `${formatNumber(summary.criticalThreats)} critical · ${formatNumber(summary.highThreats)} high`
              : "Verdict available once the run completes"
          }
          icon={ShieldAlert}
        />
      </div>

      {summary && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatTile
            label="Critical"
            value={formatNumber(summary.criticalThreats)}
            icon={ShieldAlert}
          />
          <StatTile label="High" value={formatNumber(summary.highThreats)} icon={ShieldAlert} />
          <StatTile label="Medium" value={formatNumber(summary.mediumThreats)} icon={ShieldAlert} />
          <StatTile label="Low" value={formatNumber(summary.lowThreats)} icon={ShieldAlert} />
        </div>
      )}
    </div>
  );
}
