import { Lightbulb } from "lucide-react";
import { useMemo } from "react";

import { safeCount } from "./threat-tokens";
import { GlassCard, SkeletonCard } from "@/components/common";
import type { Threat, ThreatStats } from "@/types/threats";

interface RecommendationPanelProps {
  stats: ThreatStats | null | undefined;
  threats: Threat[];
  isLoading: boolean;
}

/** Actionable guidance, derived only from findings the backend returned. */
export function RecommendationPanel({ stats, threats, isLoading }: RecommendationPanelProps) {
  const recommendations = useMemo(() => {
    const items: string[] = [];
    const severity = stats?.severityCounts ?? {};
    const total = safeCount(stats?.totalThreats);

    if (total === 0) {
      return [
        "No suspicious traffic detected in the inspected captures.",
        "Inspection completed successfully — keep uploading captures to widen coverage.",
      ];
    }

    if (safeCount(severity.CRITICAL) > 0) {
      items.push("Contain the hosts behind critical findings before further triage.");
    }
    if (safeCount(severity.HIGH) > 0) {
      items.push("Review high severity findings and confirm whether the traffic is expected.");
    }

    const ruleRecommendations = new Set<string>();
    for (const threat of threats) {
      if (threat.recommendation?.trim()) ruleRecommendations.add(threat.recommendation.trim());
    }
    items.push(...[...ruleRecommendations].slice(0, 4));

    if ((stats?.topSources?.length ?? 0) > 0) {
      items.push("Review external connections from the most active source addresses.");
    }

    return items.length > 0 ? items : ["Continue monitoring — no action is required right now."];
  }, [stats, threats]);

  if (isLoading) return <SkeletonCard />;

  return (
    <GlassCard className="space-y-4">
      <div className="flex items-center gap-2">
        <Lightbulb className="size-4 text-primary" aria-hidden="true" />
        <h2 className="text-sm font-semibold">Recommendations</h2>
      </div>
      <ul className="space-y-3">
        {recommendations.map((item) => (
          <li key={item} className="flex gap-3 text-sm text-muted-foreground">
            <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" aria-hidden="true" />
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </GlassCard>
  );
}
