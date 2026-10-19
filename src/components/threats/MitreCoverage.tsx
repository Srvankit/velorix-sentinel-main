import { Crosshair } from "lucide-react";
import { useMemo } from "react";

import { safeCount } from "./threat-tokens";
import { GlassCard, SkeletonCard } from "@/components/common";
import { MITRE_TACTICS } from "@/data/mitre";
import { cn } from "@/lib/utils";
import type { DetectionRule, Threat } from "@/types/threats";

interface MitreCoverageProps {
  threats: Threat[];
  rules: DetectionRule[];
  isLoading: boolean;
}

function baseTechniqueId(id: string | null | undefined): string | null {
  if (!id) return null;
  const match = /^T\d{4}/i.exec(id.trim());
  return match ? match[0].toUpperCase() : null;
}

/**
 * Maps observed technique identifiers onto the twelve ATT&CK Enterprise tactics.
 * Tactic attribution is derived locally from the public framework reference — no
 * extra API request is made for it.
 */
export function MitreCoverage({ threats, rules, isLoading }: MitreCoverageProps) {
  const { detections, ruleCoverage } = useMemo(() => {
    const techniqueToTactic = new Map<string, string>();
    for (const tactic of MITRE_TACTICS) {
      for (const technique of tactic.techniques) {
        techniqueToTactic.set(technique.id.toUpperCase(), tactic.id);
      }
    }

    const detected = new Map<string, number>();
    for (const threat of threats) {
      const tacticId = techniqueToTactic.get(baseTechniqueId(threat.mitreTechnique) ?? "");
      if (!tacticId) continue;
      detected.set(tacticId, (detected.get(tacticId) ?? 0) + 1);
    }

    const covered = new Map<string, number>();
    for (const rule of rules) {
      const tacticId = techniqueToTactic.get(baseTechniqueId(rule.mitreTechnique) ?? "");
      if (!tacticId) continue;
      covered.set(tacticId, (covered.get(tacticId) ?? 0) + 1);
    }

    return { detections: detected, ruleCoverage: covered };
  }, [threats, rules]);

  if (isLoading) {
    return (
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {Array.from({ length: 8 }).map((_, index) => (
          <SkeletonCard key={index} />
        ))}
      </div>
    );
  }

  return (
    <section aria-label="MITRE ATT&CK coverage" className="space-y-4">
      <div className="flex items-center gap-2">
        <Crosshair className="size-4 text-primary" aria-hidden="true" />
        <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
          MITRE ATT&amp;CK mapping
        </h2>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {MITRE_TACTICS.map((tactic, index) => {
          const hits = safeCount(detections.get(tactic.id));
          const covering = safeCount(ruleCoverage.get(tactic.id));
          return (
            <GlassCard
              key={tactic.id}
              delay={index * 0.02}
              className={cn(
                "p-4 transition-colors",
                hits > 0 ? "border-primary/50 bg-primary/5" : "hover:border-primary/30",
              )}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold">{tactic.name}</p>
                  <p className="text-[11px] uppercase tracking-wide text-muted-foreground">
                    {tactic.id}
                  </p>
                </div>
                <span
                  className={cn(
                    "rounded-full border px-2 py-0.5 text-xs font-semibold tabular-nums",
                    hits > 0
                      ? "border-primary/40 bg-primary/15 text-primary"
                      : "border-border text-muted-foreground",
                  )}
                >
                  {hits}
                </span>
              </div>
              <p className="mt-3 text-xs text-muted-foreground">
                {hits > 0
                  ? `${hits} finding${hits === 1 ? "" : "s"} mapped to this tactic.`
                  : covering > 0
                    ? `${covering} detection rule${covering === 1 ? "" : "s"} watching, no detections yet.`
                    : "Awaiting backend analysis."}
              </p>
            </GlassCard>
          );
        })}
      </div>
    </section>
  );
}
