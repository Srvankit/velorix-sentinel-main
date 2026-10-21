import { Activity, AlertTriangle, Gauge, Globe, Layers, ShieldAlert, Timer } from "lucide-react";

import { StatTile } from "./StatTile";
import { ErrorState, SkeletonStat } from "@/components/common";
import type { Analysis, AnalysisThreatSummary } from "@/types/analysis";
import { formatDuration, isTruncatedCaptureWarningVisible } from "@/utils/analysisFormat";
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

  const isDetectionComplete = analysis.status === "COMPLETED" && Boolean(summary);

  const maliciousValue = isDetectionComplete
    ? formatNumber(analysis.maliciousPackets)
    : "—";

  const maliciousHint = isDetectionComplete
    ? `${formatNumber(analysis.safePackets)} clean frames`
    : analysis.status === "FAILED"
      ? "Inspection failed"
      : "Awaiting scan";

  const riskValue = isDetectionComplete
    ? `${summary!.riskScore}/100`
    : "—";

  const riskHint = isDetectionComplete
    ? summary!.overallStatus.toLowerCase().replace(/_/g, " ")
    : analysis.status === "FAILED"
      ? "Inspection failed"
      : analysis.status === "PROCESSING"
        ? "Scanning in progress…"
        : "Awaiting scan";

  const threatsValue = isDetectionComplete
    ? formatNumber(summary!.totalThreats)
    : "—";

  const threatsHint = isDetectionComplete
    ? `${formatNumber(summary!.criticalThreats)} critical · ${formatNumber(summary!.highThreats)} high`
    : analysis.status === "FAILED"
      ? "Inspection failed"
      : "Verdict available once scan completes";

  return (
    <div className="space-y-6">
      {isTruncatedCaptureWarningVisible(analysis.status, analysis.truncated) && (
        <div
          role="status"
          aria-live="polite"
          data-testid="truncated-capture-warning"
          className="flex items-center gap-3 rounded-lg border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-amber-200"
        >
          <AlertTriangle className="size-5 shrink-0 text-amber-400" aria-hidden="true" />
          <span>Capture truncated: analysis used all valid frames available before the file ended.</span>
        </div>
      )}

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
          value={maliciousValue}
          hint={maliciousHint}
          icon={ShieldAlert}
        />
        <StatTile
          label="Risk score"
          value={riskValue}
          hint={riskHint}
          icon={Gauge}
        />
        <StatTile
          label="Threats detected"
          value={threatsValue}
          hint={threatsHint}
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
