import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { SkeletonChart } from "@/components/common/Skeletons";
import { cn } from "@/lib/utils";

interface ChartCardProps {
  title: string;
  description?: string;
  icon?: LucideIcon;
  actions?: ReactNode;
  isLoading?: boolean;
  error?: { message: string } | null;
  onRetry?: () => void;
  /** When true the card renders its empty state instead of the chart. */
  isEmpty?: boolean;
  emptyTitle?: string;
  emptyDescription?: string;
  className?: string;
  children: ReactNode;
}

/**
 * Uniform frame around every visualisation: title, loading skeleton, error and
 * empty handling. Charts themselves stay presentational and data-agnostic.
 */
export function ChartCard({
  title,
  description,
  icon: Icon,
  actions,
  isLoading = false,
  error = null,
  onRetry,
  isEmpty = false,
  emptyTitle = "No data yet",
  emptyDescription = "This chart fills in once an inspection has been analysed.",
  className,
  children,
}: ChartCardProps) {
  return (
    <section className={cn("glass-panel rounded-2xl p-5", className)}>
      <header className="mb-4 flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          {Icon && (
            <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
              <Icon className="size-4" aria-hidden="true" />
            </span>
          )}
          <div>
            <h2 className="text-sm font-semibold tracking-tight">{title}</h2>
            {description && <p className="text-xs text-muted-foreground">{description}</p>}
          </div>
        </div>
        {actions}
      </header>

      {isLoading ? (
        <SkeletonChart bars={16} className="h-48" label={`Loading ${title}`} />
      ) : error ? (
        <ErrorState error={error as Error} onRetry={onRetry} />
      ) : isEmpty ? (
        <EmptyState title={emptyTitle} description={emptyDescription} className="py-10" />
      ) : (
        children
      )}
    </section>
  );
}
