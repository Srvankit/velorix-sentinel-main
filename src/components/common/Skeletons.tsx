import { cn } from "@/lib/utils";

/**
 * Reusable skeleton primitives.
 *
 * These stand in for data that will arrive from the backend. They are purely
 * presentational — no component in this file invents a value.
 */

export function Skeleton({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn("block animate-pulse rounded-md bg-foreground/8", className)}
    />
  );
}

interface SkeletonBlockProps {
  className?: string;
  /** Accessible description of what is loading. */
  label?: string;
}

/** Multi-line text placeholder. */
export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <span className={cn("block space-y-2", className)}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className={cn("h-3", i === lines - 1 ? "w-2/3" : "w-full")} />
      ))}
    </span>
  );
}

/** Placeholder for a single KPI / metric tile. */
export function SkeletonStat({ className, label }: SkeletonBlockProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn("flex flex-col items-center gap-3 text-center", className)}
    >
      <Skeleton className="h-10 w-28 rounded-lg" />
      <Skeleton className="h-3 w-20" />
      {label && <span className="sr-only">{label}</span>}
    </div>
  );
}

/** Placeholder for a bar/line chart panel. */
export function SkeletonChart({
  bars = 12,
  className,
  label,
}: SkeletonBlockProps & { bars?: number }) {
  return (
    <div role="status" aria-live="polite" className={cn("flex h-24 items-end gap-1.5", className)}>
      {Array.from({ length: bars }).map((_, i) => (
        <Skeleton
          key={i}
          className="flex-1 rounded-t"
          // deterministic staircase — a shape, not a data point
        />
      ))}
      {label && <span className="sr-only">{label}</span>}
    </div>
  );
}

/** Placeholder rows for tabular data. */
export function SkeletonTable({
  rows = 5,
  columns = 4,
  className,
  label,
}: SkeletonBlockProps & { rows?: number; columns?: number }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn("overflow-hidden rounded-lg border border-border", className)}
    >
      <div className="flex gap-4 border-b border-border bg-elevated/60 px-3 py-2">
        {Array.from({ length: columns }).map((_, i) => (
          <Skeleton key={i} className="h-2.5 flex-1" />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex gap-4 border-b border-border px-3 py-2.5 last:border-b-0">
          {Array.from({ length: columns }).map((_, c) => (
            <Skeleton key={c} className="h-2.5 flex-1" />
          ))}
        </div>
      ))}
      {label && <span className="sr-only">{label}</span>}
    </div>
  );
}

/** Placeholder for a list of records. */
export function SkeletonList({
  items = 3,
  className,
  label,
}: SkeletonBlockProps & { items?: number }) {
  return (
    <div role="status" aria-live="polite" className={className}>
      <ul className="space-y-2">
        {Array.from({ length: items }).map((_, i) => (
          <li
            key={i}
            className="flex items-center justify-between gap-4 rounded-lg border border-border bg-elevated/60 px-3 py-2.5"
          >
            <span className="flex-1 space-y-1.5">
              <Skeleton className="h-2.5 w-1/2" />
              <Skeleton className="h-2 w-1/3" />
            </span>
            <Skeleton className="h-4 w-14 rounded-full" />
          </li>
        ))}
      </ul>
      {label && <span className="sr-only">{label}</span>}
    </div>
  );
}

/** Card-shaped placeholder used while a panel's data loads. */
export function SkeletonCard({ className, label }: SkeletonBlockProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn("space-y-4 rounded-2xl border border-border bg-card/70 p-6", className)}
    >
      <Skeleton className="h-4 w-1/3" />
      <SkeletonText lines={3} />
      {label && <span className="sr-only">{label}</span>}
    </div>
  );
}
