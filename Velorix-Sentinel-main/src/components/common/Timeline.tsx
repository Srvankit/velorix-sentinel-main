import type { LucideIcon } from "lucide-react";
import { Dot } from "lucide-react";

import { cn } from "@/lib/utils";

export interface TimelineItem {
  id: string;
  title: string;
  description?: string | null;
  timestamp: string;
  icon?: LucideIcon;
  /** Visual tone of the marker. */
  tone?: "neutral" | "success" | "warning" | "danger";
}

const TONE_CLASSES: Record<NonNullable<TimelineItem["tone"]>, string> = {
  neutral: "border-border bg-muted text-muted-foreground",
  success: "border-success/40 bg-success/15 text-success",
  warning: "border-warning/40 bg-warning/15 text-warning",
  danger: "border-destructive/40 bg-destructive/15 text-destructive",
};

interface TimelineProps {
  items: TimelineItem[];
  className?: string;
}

/** Vertical event timeline shared by the activity feed and analysis detail views. */
export function Timeline({ items, className }: TimelineProps) {
  return (
    <ol className={cn("relative space-y-5 ps-6", className)}>
      <span className="absolute inset-y-1 left-[11px] w-px bg-border" aria-hidden="true" />
      {items.map((item) => {
        const Icon = item.icon ?? Dot;
        return (
          <li key={item.id} className="relative">
            <span
              className={cn(
                "absolute top-0.5 -left-6 grid size-6 place-items-center rounded-full border",
                TONE_CLASSES[item.tone ?? "neutral"],
              )}
              aria-hidden="true"
            >
              <Icon className="size-3" />
            </span>
            <p className="text-sm font-medium text-foreground">{item.title}</p>
            {item.description && (
              <p className="text-sm text-muted-foreground">{item.description}</p>
            )}
            <p className="mt-0.5 font-mono text-xs text-muted-foreground">{item.timestamp}</p>
          </li>
        );
      })}
    </ol>
  );
}
