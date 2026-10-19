import { FileSearch } from "lucide-react";

import { EmptyState, RiskBadge, SkeletonList, StatusBadge } from "@/components/common";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import type { Analysis } from "@/types/analysis";
import { isRunning } from "@/types/analysis";
import { formatNumber, formatRelativeTime } from "@/utils/format";

interface AnalysisRunListProps {
  runs: Analysis[];
  selectedId: string | null;
  onSelect: (run: Analysis) => void;
  isLoading: boolean;
}

/** Left rail of inspection runs; live runs show their pipeline stage and progress. */
export function AnalysisRunList({ runs, selectedId, onSelect, isLoading }: AnalysisRunListProps) {
  if (isLoading) {
    return <SkeletonList items={5} />;
  }

  if (runs.length === 0) {
    return (
      <EmptyState
        icon={FileSearch}
        title="No inspection runs yet"
        description="Upload a capture and start an inspection to see decoded traffic here."
      />
    );
  }

  return (
    <ul className="space-y-2">
      {runs.map((run) => (
        <li key={run.id}>
          <button
            type="button"
            onClick={() => onSelect(run)}
            aria-current={selectedId === run.id}
            className={cn(
              "focus-ring w-full rounded-2xl border p-4 text-left transition-colors",
              selectedId === run.id
                ? "border-primary/50 bg-primary/10"
                : "border-border bg-surface/50 hover:border-primary/30",
            )}
          >
            <div className="flex items-start justify-between gap-3">
              <p className="min-w-0 truncate text-sm font-medium">{run.originalFileName}</p>
              <StatusBadge status={run.status} />
            </div>
            <div className="mt-2 flex items-center justify-between gap-2 text-xs text-muted-foreground">
              <span>{formatNumber(run.totalPackets)} packets</span>
              <span>{formatRelativeTime(run.createdAt)}</span>
            </div>
            {isRunning(run) ? (
              <div className="mt-3 space-y-1">
                <Progress value={run.progressPercent} className="h-1.5" />
                <p className="text-xs text-muted-foreground">
                  {run.stage.toLowerCase()} · {run.progressPercent}%
                </p>
              </div>
            ) : (
              <div className="mt-3">
                <RiskBadge score={run.riskScore} />
              </div>
            )}
          </button>
        </li>
      ))}
    </ul>
  );
}
