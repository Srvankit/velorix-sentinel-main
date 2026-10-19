import { ShieldCheck } from "lucide-react";
import { useMemo } from "react";

import { postureFromStatus, safeCount, safeScore } from "./threat-tokens";
import { GlassCard, SkeletonCard } from "@/components/common";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import { formatNumber } from "@/utils/format";
import type { Threat, ThreatStats } from "@/types/threats";

interface RiskPanelProps {
  stats: ThreatStats | null | undefined;
  threats: Threat[];
  isLoading: boolean;
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-border/60 py-2 last:border-0">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm font-medium tabular-nums">{value}</span>
    </div>
  );
}

/** Posture summary derived from the detection statistics endpoint. */
export function RiskAssessmentPanel({ stats, threats, isLoading }: RiskPanelProps) {
  const derived = useMemo(() => {
    const hosts = new Set<string>();
    let confidenceTotal = 0;
    let confidenceSamples = 0;
    let packets = 0;

    for (const threat of threats) {
      if (threat.sourceIp) hosts.add(threat.sourceIp);
      if (threat.destinationIp) hosts.add(threat.destinationIp);
      if (Number.isFinite(threat.confidencePercent)) {
        confidenceTotal += threat.confidencePercent;
        confidenceSamples += 1;
      }
      if (Number.isFinite(threat.packetCount)) packets += threat.packetCount;
    }

    return {
      suspiciousHosts: hosts.size,
      averageConfidence:
        confidenceSamples > 0 ? Math.round(confidenceTotal / confidenceSamples) : null,
      involvedPackets: packets,
    };
  }, [threats]);

  if (isLoading) return <SkeletonCard />;

  const risk = safeScore(stats?.riskScore);
  const posture = postureFromStatus(stats?.overallStatus ?? null);

  return (
    <GlassCard className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <ShieldCheck className="size-4 text-primary" aria-hidden="true" />
          <h2 className="text-sm font-semibold">Risk assessment</h2>
        </div>
        <span className={cn("text-xs font-semibold uppercase", posture.className)}>
          {posture.label}
        </span>
      </div>

      <div className="space-y-2">
        <div className="flex items-baseline justify-between">
          <span className="text-xs text-muted-foreground">Overall risk</span>
          <span className="text-2xl font-semibold tabular-nums">
            {risk === null ? "Awaiting analysis" : `${risk} / 100`}
          </span>
        </div>
        <Progress value={risk ?? 0} aria-label="Overall risk score" />
      </div>

      <div>
        <Row label="Total findings" value={formatNumber(safeCount(stats?.totalThreats))} />
        <Row
          label="Attack surface (unique sources)"
          value={formatNumber(safeCount(stats?.topSources?.length))}
        />
        <Row label="Suspicious hosts" value={formatNumber(derived.suspiciousHosts)} />
        <Row label="Packets implicated" value={formatNumber(derived.involvedPackets)} />
        <Row
          label="Average confidence"
          value={
            derived.averageConfidence === null
              ? "Awaiting analysis"
              : `${derived.averageConfidence}%`
          }
        />
      </div>
    </GlassCard>
  );
}
