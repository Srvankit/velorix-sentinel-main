import { Radar } from "lucide-react";
import { useMemo } from "react";

import { safeCount, textOrDash } from "./threat-tokens";
import { EmptyState, GlassCard, SeverityBadge, SkeletonCard } from "@/components/common";
import { cn } from "@/lib/utils";
import { formatDateTime, humaniseEnum } from "@/utils/format";
import type { DetectionRule, Threat } from "@/types/threats";

interface RuleGridProps {
  rules: DetectionRule[];
  threats: Threat[];
  isLoading: boolean;
}

/** Rule catalogue with trigger counts derived from the loaded finding feed. */
export function DetectionRuleGrid({ rules, threats, isLoading }: RuleGridProps) {
  const activity = useMemo(() => {
    const map = new Map<string, { count: number; lastTriggered: number }>();
    for (const threat of threats) {
      if (!threat.detectionRule) continue;
      const current = map.get(threat.detectionRule) ?? { count: 0, lastTriggered: 0 };
      const detected = threat.detectedAt ? Date.parse(threat.detectedAt) : 0;
      map.set(threat.detectionRule, {
        count: current.count + 1,
        lastTriggered: Math.max(current.lastTriggered, Number.isFinite(detected) ? detected : 0),
      });
    }
    return map;
  }, [threats]);

  if (isLoading) {
    return (
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 6 }).map((_, index) => (
          <SkeletonCard key={index} />
        ))}
      </div>
    );
  }

  if (rules.length === 0) {
    return (
      <EmptyState
        title="No detection rules published"
        description="The rule catalogue appears here once the detection engine reports its rules."
        icon={Radar}
      />
    );
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {rules.map((rule, index) => {
        const stats = activity.get(rule.id);
        const triggers = safeCount(stats?.count);
        const active = rule.enabled && triggers > 0;
        return (
          <GlassCard
            key={rule.id}
            delay={index * 0.02}
            className="space-y-3 p-5 transition-colors hover:border-primary/40"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold">{textOrDash(rule.name)}</p>
                <p className="truncate font-mono text-[11px] text-muted-foreground">{rule.id}</p>
              </div>
              <span
                className={cn(
                  "rounded-full border px-2 py-0.5 text-[11px] font-medium uppercase tracking-wide",
                  active
                    ? "border-success/40 bg-success/10 text-success"
                    : rule.enabled
                      ? "border-info/40 bg-info/10 text-info"
                      : "border-border bg-muted text-muted-foreground",
                )}
              >
                {active ? "Active" : rule.enabled ? "Armed" : "Inactive"}
              </span>
            </div>

            <p className="line-clamp-2 text-xs text-muted-foreground">
              {rule.description?.trim() || humaniseEnum(rule.threatType ?? "UNKNOWN")}
            </p>

            <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3 text-xs text-muted-foreground">
              <SeverityBadge severity={rule.baseSeverity ?? "LOW"} />
              <span className="tabular-nums">
                {triggers} trigger{triggers === 1 ? "" : "s"}
              </span>
              <span>
                {stats?.lastTriggered
                  ? formatDateTime(new Date(stats.lastTriggered).toISOString())
                  : "Never triggered"}
              </span>
            </div>
          </GlassCard>
        );
      })}
    </div>
  );
}
