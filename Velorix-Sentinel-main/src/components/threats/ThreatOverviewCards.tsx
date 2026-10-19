import {
  Activity,
  AlertOctagon,
  AlertTriangle,
  Gauge,
  ShieldAlert,
  ShieldCheck,
  ShieldQuestion,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { AnimatedCounter } from "./AnimatedCounter";
import { postureFromStatus, safeCount, safeScore } from "./threat-tokens";
import { GlassCard, SkeletonStat } from "@/components/common";
import { cn } from "@/lib/utils";
import type { ThreatStats } from "@/types/threats";

interface OverviewProps {
  stats: ThreatStats | null | undefined;
  isLoading: boolean;
}

interface Tile {
  key: string;
  label: string;
  icon: LucideIcon;
  accent: string;
  value: number;
  fallback?: string;
  suffix?: string;
  hint?: string;
}

/** Top KPI strip: counts always resolve to 0 rather than NaN/undefined. */
export function ThreatOverviewCards({ stats, isLoading }: OverviewProps) {
  if (isLoading) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 8 }).map((_, index) => (
          <SkeletonStat key={index} />
        ))}
      </div>
    );
  }

  const severity = stats?.severityCounts ?? {};
  const score = safeScore(stats?.riskScore);
  const securityScore = score === null ? null : Math.max(0, Math.min(100, 100 - score));
  const posture = postureFromStatus(stats?.overallStatus ?? null);

  const tiles: Tile[] = [
    {
      key: "total",
      label: "Total threats",
      icon: ShieldAlert,
      accent: "text-foreground",
      value: safeCount(stats?.totalThreats),
    },
    {
      key: "critical",
      label: "Critical",
      icon: AlertOctagon,
      accent: "text-critical",
      value: safeCount(severity.CRITICAL),
    },
    {
      key: "high",
      label: "High severity",
      icon: AlertTriangle,
      accent: "text-destructive",
      value: safeCount(severity.HIGH),
    },
    {
      key: "medium",
      label: "Medium severity",
      icon: Activity,
      accent: "text-warning",
      value: safeCount(severity.MEDIUM),
    },
    {
      key: "low",
      label: "Low severity",
      icon: ShieldQuestion,
      accent: "text-info",
      value: safeCount(severity.LOW),
    },
    {
      key: "security-score",
      label: "Security score",
      icon: ShieldCheck,
      accent: "text-success",
      value: securityScore ?? 0,
      fallback: securityScore === null ? "Awaiting analysis" : undefined,
      suffix: " / 100",
    },
    {
      key: "risk-score",
      label: "Risk score",
      icon: Gauge,
      accent: "text-primary",
      value: score ?? 0,
      fallback: score === null ? "Awaiting analysis" : undefined,
      suffix: " / 100",
    },
  ];

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {tiles.map((tile, index) => (
        <GlassCard
          key={tile.key}
          delay={index * 0.04}
          className="p-5 transition-colors hover:border-primary/40"
        >
          <div className="flex items-start justify-between gap-3">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {tile.label}
            </p>
            <tile.icon className={cn("size-4", tile.accent)} aria-hidden="true" />
          </div>
          <p
            className={cn(
              "mt-3 font-semibold tabular-nums",
              tile.fallback ? "text-base text-muted-foreground" : "text-3xl",
              !tile.fallback && tile.accent,
            )}
          >
            <AnimatedCounter value={tile.value} fallback={tile.fallback} suffix={tile.suffix} />
          </p>
        </GlassCard>
      ))}

      <GlassCard delay={0.28} className="p-5 transition-colors hover:border-primary/40">
        <div className="flex items-start justify-between gap-3">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Threat status
          </p>
          <ShieldCheck className={cn("size-4", posture.className)} aria-hidden="true" />
        </div>
        <p className={cn("mt-3 text-2xl font-semibold", posture.className)}>{posture.label}</p>
        <p className="mt-1 text-xs text-muted-foreground">
          Derived from the latest detection verdict.
        </p>
      </GlassCard>
    </div>
  );
}
