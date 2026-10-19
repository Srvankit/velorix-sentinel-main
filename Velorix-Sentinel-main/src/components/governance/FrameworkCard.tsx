import { ArrowRight, ShieldCheck } from "lucide-react";
import { Link } from "@tanstack/react-router";

import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import type { FrameworkDefinition } from "@/data/compliance-frameworks";
import type { FrameworkAssessment } from "@/types/governance";

interface FrameworkCardProps {
  framework: FrameworkDefinition;
  assessment?: FrameworkAssessment | null;
  className?: string;
}

/**
 * Framework summary tile. Coverage bar renders only when the backend supplies a
 * real assessment; otherwise the card states that no assessment exists yet.
 */
export function FrameworkCard({ framework, assessment = null, className }: FrameworkCardProps) {
  const coverage = assessment?.coveragePercent ?? null;

  return (
    <Link
      to="/reports/compliance/$framework"
      params={{ framework: framework.slug }}
      className={cn(
        "group flex flex-col gap-3 rounded-2xl border border-border/70 bg-card/40 p-4 backdrop-blur-xl transition-colors hover:border-primary/40",
        className,
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{framework.shortName}</p>
          <p className="mt-0.5 truncate text-[11px] text-muted-foreground">{framework.authority}</p>
        </div>
        <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
          <ShieldCheck className="size-4" aria-hidden="true" />
        </span>
      </div>

      <p className="line-clamp-2 text-xs text-muted-foreground">{framework.focus}</p>

      <div className="mt-auto space-y-1.5">
        {coverage !== null ? (
          <>
            <div className="flex items-baseline justify-between">
              <span className="text-[11px] text-muted-foreground">Control coverage</span>
              <span className="font-mono text-sm font-semibold">{coverage}%</span>
            </div>
            <Progress value={coverage} className="h-1.5" />
          </>
        ) : (
          <div className="rounded-lg border border-dashed border-border/80 bg-muted/15 px-2.5 py-1.5">
            <p className="text-[11px] font-medium text-muted-foreground">
              Awaiting Live Security Data
            </p>
          </div>
        )}
        <div className="flex items-center justify-between pt-1">
          <span className="text-[11px] text-muted-foreground">
            {framework.requirements.length} control groups
          </span>
          <ArrowRight
            className="size-3.5 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-primary"
            aria-hidden="true"
          />
        </div>
      </div>
    </Link>
  );
}
