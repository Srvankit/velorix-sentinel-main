import {
  ClipboardList,
  FileBarChart,
  Fingerprint,
  Lightbulb,
  ScanSearch,
  ShieldQuestion,
  TriangleAlert,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

interface CommandAction {
  label: string;
  description: string;
  icon: LucideIcon;
}

const ACTIONS: CommandAction[] = [
  {
    label: "Explain alert",
    description: "Plain-language breakdown of a detection",
    icon: ShieldQuestion,
  },
  {
    label: "Summarize logs",
    description: "Condense capture activity into findings",
    icon: ClipboardList,
  },
  {
    label: "Generate report",
    description: "Executive SOC summary with actions",
    icon: FileBarChart,
  },
  {
    label: "Investigate threat",
    description: "Guided triage across related events",
    icon: ScanSearch,
  },
  { label: "Create IOC", description: "Draft indicators from observed traffic", icon: Fingerprint },
  {
    label: "Risk assessment",
    description: "Score exposure across the estate",
    icon: TriangleAlert,
  },
  {
    label: "SOC recommendations",
    description: "Prioritised hardening next steps",
    icon: Lightbulb,
  },
];

/** Quick AI actions. Every action stays disabled until inference is connected. */
export function AiCommandCenter({ className }: { className?: string }) {
  return (
    <section className={cn("glass-panel rounded-2xl p-5", className)}>
      <header className="mb-4 grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold tracking-tight">AI command center</h2>
          <p className="text-xs text-muted-foreground">One-click analyst workflows</p>
        </div>
        <span className="shrink-0 rounded-full border border-border px-2 py-0.5 text-[10px] font-medium tracking-wider text-muted-foreground uppercase">
          Disabled
        </span>
      </header>

      <ul className="grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
        {ACTIONS.map((action) => (
          <li key={action.label}>
            <button
              type="button"
              disabled
              title="Available once the AI backend is connected"
              className="flex w-full cursor-not-allowed items-start gap-3 rounded-xl border border-border bg-surface/60 p-3 text-left opacity-60"
            >
              <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
                <action.icon className="size-4" aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium">{action.label}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {action.description}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
