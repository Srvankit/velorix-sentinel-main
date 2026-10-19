import { Radio } from "lucide-react";

import { cn } from "@/lib/utils";

interface AwaitingFeedProps {
  /** What would be displayed here once a feed is connected. */
  label?: string;
  detail?: string;
  className?: string;
  compact?: boolean;
}

/**
 * Canonical notice for every intelligence surface that depends on a live feed.
 * Keeps the product honest: no fabricated indicators, CVEs or attacks.
 */
export function AwaitingFeed({
  label = "Awaiting Live Threat Intelligence",
  detail = "Connect an intelligence source to populate this view. No data is fabricated.",
  className,
  compact = false,
}: AwaitingFeedProps) {
  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-xl border border-dashed border-border/80 bg-muted/20 text-muted-foreground",
        compact ? "px-3 py-2.5" : "px-4 py-5",
        className,
      )}
      role="status"
    >
      <span className="relative mt-0.5 grid size-7 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
        <Radio className="size-3.5" aria-hidden="true" />
        <span
          className="absolute inset-0 animate-ping rounded-lg bg-primary/10"
          aria-hidden="true"
        />
      </span>
      <div className="space-y-1">
        <p className={cn("font-medium text-foreground", compact ? "text-xs" : "text-sm")}>
          {label}
        </p>
        {!compact && <p className="text-xs leading-relaxed">{detail}</p>}
      </div>
    </div>
  );
}
