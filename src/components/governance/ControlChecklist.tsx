import { CircleDashed, CircleSlash, CircleCheck, CircleAlert } from "lucide-react";

import { cn } from "@/lib/utils";
import type { FrameworkRequirement } from "@/data/compliance-frameworks";
import type { ControlAssessment, ControlState } from "@/types/governance";

const STATE_META: Record<ControlState, { label: string; icon: typeof CircleDashed; tone: string }> =
  {
    MET: { label: "Met", icon: CircleCheck, tone: "text-emerald-400" },
    PARTIAL: { label: "Partial", icon: CircleAlert, tone: "text-amber-400" },
    GAP: { label: "Gap", icon: CircleSlash, tone: "text-primary" },
    NOT_ASSESSED: { label: "Not assessed", icon: CircleDashed, tone: "text-muted-foreground" },
  };

interface ControlChecklistProps {
  requirements: FrameworkRequirement[];
  assessments?: ControlAssessment[];
  className?: string;
}

/**
 * Published control structure joined with backend assessment state. Controls
 * with no assessment explicitly read "Not assessed" — never assumed compliant.
 */
export function ControlChecklist({
  requirements,
  assessments = [],
  className,
}: ControlChecklistProps) {
  const byId = new Map(assessments.map((item) => [item.controlId, item]));

  return (
    <ul className={cn("divide-y divide-border/60", className)}>
      {requirements.map((requirement) => {
        const assessment = byId.get(requirement.id);
        const state: ControlState = assessment?.state ?? "NOT_ASSESSED";
        const meta = STATE_META[state];
        const Icon = meta.icon;

        return (
          <li key={requirement.id} className="flex items-start gap-3 py-3">
            <Icon className={cn("mt-0.5 size-4 shrink-0", meta.tone)} aria-hidden="true" />
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-baseline gap-2">
                <span className="font-mono text-xs text-primary">{requirement.id}</span>
                <span className="text-sm font-medium">{requirement.title}</span>
              </div>
              <p className="mt-0.5 text-xs text-muted-foreground">{requirement.description}</p>
              {assessment?.note && (
                <p className="mt-1 text-[11px] text-muted-foreground italic">{assessment.note}</p>
              )}
            </div>
            <div className="shrink-0 text-right">
              <p className={cn("text-[11px] font-medium", meta.tone)}>{meta.label}</p>
              <p className="text-[10px] text-muted-foreground">
                {assessment ? `${assessment.evidenceCount} evidence` : "No evidence"}
              </p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
