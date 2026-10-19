import { cn } from "@/lib/utils";
import type { RiskEntry, RiskImpact, RiskLikelihood } from "@/types/governance";

const LIKELIHOODS: RiskLikelihood[] = ["ALMOST_CERTAIN", "LIKELY", "POSSIBLE", "UNLIKELY", "RARE"];
const IMPACTS: RiskImpact[] = ["NEGLIGIBLE", "MINOR", "MODERATE", "MAJOR", "SEVERE"];

const LIKELIHOOD_LABEL: Record<RiskLikelihood, string> = {
  ALMOST_CERTAIN: "Almost certain",
  LIKELY: "Likely",
  POSSIBLE: "Possible",
  UNLIKELY: "Unlikely",
  RARE: "Rare",
};

const IMPACT_LABEL: Record<RiskImpact, string> = {
  NEGLIGIBLE: "Negligible",
  MINOR: "Minor",
  MODERATE: "Moderate",
  MAJOR: "Major",
  SEVERE: "Severe",
};

/** 1..25 severity product used only to tint the empty grid, never as a metric. */
function cellWeight(likelihood: RiskLikelihood, impact: RiskImpact): number {
  const l = 5 - LIKELIHOODS.indexOf(likelihood);
  const i = IMPACTS.indexOf(impact) + 1;
  return l * i;
}

function cellTone(weight: number): string {
  if (weight >= 17) return "bg-primary/25 border-primary/40";
  if (weight >= 11) return "bg-amber-500/20 border-amber-500/30";
  if (weight >= 6) return "bg-yellow-500/10 border-yellow-500/20";
  return "bg-emerald-500/10 border-emerald-500/20";
}

interface RiskMatrixProps {
  entries?: RiskEntry[];
  onSelect?: (entry: RiskEntry) => void;
  className?: string;
}

/**
 * 5x5 likelihood/impact heat map. The grid itself is a static scale; risk
 * counts only appear once the register returns entries from the backend.
 */
export function RiskMatrix({ entries = [], onSelect, className }: RiskMatrixProps) {
  const grouped = new Map<string, RiskEntry[]>();
  for (const entry of entries) {
    const key = `${entry.likelihood}:${entry.impact}`;
    grouped.set(key, [...(grouped.get(key) ?? []), entry]);
  }

  return (
    <div className={cn("w-full overflow-x-auto", className)}>
      <div className="min-w-[560px]">
        <div className="grid grid-cols-[auto_repeat(5,minmax(0,1fr))] gap-1.5">
          <div aria-hidden="true" />
          {IMPACTS.map((impact) => (
            <div
              key={impact}
              className="pb-1 text-center text-[10px] tracking-wide text-muted-foreground uppercase"
            >
              {IMPACT_LABEL[impact]}
            </div>
          ))}

          {LIKELIHOODS.map((likelihood) => (
            <div key={likelihood} className="contents">
              <div className="flex items-center pr-2 text-right text-[10px] tracking-wide text-muted-foreground uppercase">
                {LIKELIHOOD_LABEL[likelihood]}
              </div>
              {IMPACTS.map((impact) => {
                const key = `${likelihood}:${impact}`;
                const cellEntries = grouped.get(key) ?? [];
                const weight = cellWeight(likelihood, impact);
                const interactive = cellEntries.length > 0 && Boolean(onSelect);
                return (
                  <button
                    key={key}
                    type="button"
                    disabled={!interactive}
                    onClick={() => interactive && onSelect?.(cellEntries[0])}
                    aria-label={`${LIKELIHOOD_LABEL[likelihood]} likelihood, ${IMPACT_LABEL[impact]} impact: ${cellEntries.length} risks`}
                    className={cn(
                      "grid aspect-[4/3] place-items-center rounded-lg border transition-transform",
                      cellTone(weight),
                      interactive && "cursor-pointer hover:scale-[1.03]",
                    )}
                  >
                    {cellEntries.length > 0 ? (
                      <span className="font-mono text-sm font-semibold">{cellEntries.length}</span>
                    ) : (
                      <span className="text-[10px] text-muted-foreground/50">—</span>
                    )}
                  </button>
                );
              })}
            </div>
          ))}
        </div>

        <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
          <p className="text-[11px] text-muted-foreground">
            Impact increases left to right; likelihood increases bottom to top.
          </p>
          {entries.length === 0 && (
            <p className="text-[11px] font-medium text-muted-foreground">
              Awaiting Live Security Data — no risks plotted
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
