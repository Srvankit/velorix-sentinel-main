import { Activity, BrainCircuit, Database, Gauge, Radar, ShieldCheck } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

interface StatusRow {
  label: string;
  value: string;
  icon: LucideIcon;
  tone: "pending" | "ready" | "info";
}

const ROWS: StatusRow[] = [
  { label: "Model", value: "Not selected", icon: BrainCircuit, tone: "pending" },
  { label: "Availability", value: "Awaiting integration", icon: Activity, tone: "pending" },
  { label: "Latency", value: "Awaiting Data", icon: Gauge, tone: "pending" },
  { label: "Context window", value: "Awaiting Data", icon: Database, tone: "pending" },
  { label: "Knowledge base", value: "Static reference library", icon: ShieldCheck, tone: "info" },
  { label: "Threat intelligence", value: "Not connected", icon: Radar, tone: "pending" },
];

const TONE = {
  pending: "text-muted-foreground",
  ready: "text-success",
  info: "text-info",
} as const;

/** Read-only status board for the copilot runtime. No values are invented. */
export function AiStatusPanel({ className }: { className?: string }) {
  return (
    <section className={cn("glass-panel rounded-2xl p-5", className)}>
      <header className="mb-4">
        <h2 className="text-sm font-semibold tracking-tight">AI status</h2>
        <p className="text-xs text-muted-foreground">Inference runtime and intelligence sources</p>
      </header>

      <dl className="space-y-3">
        {ROWS.map((row) => (
          <div
            key={row.label}
            className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 text-sm"
          >
            <dt className="flex min-w-0 items-center gap-2 text-muted-foreground">
              <row.icon className="size-3.5 shrink-0" aria-hidden="true" />
              <span className="truncate text-xs">{row.label}</span>
            </dt>
            <dd className={cn("shrink-0 text-xs font-medium", TONE[row.tone])}>{row.value}</dd>
          </div>
        ))}
      </dl>

      <div className="mt-5 rounded-xl border border-primary/30 bg-primary/10 px-4 py-3">
        <p className="flex items-center gap-2 text-xs font-semibold text-primary">
          <span className="size-1.5 animate-pulse rounded-full bg-primary" aria-hidden="true" />
          Ready for AI Integration
        </p>
        <p className="mt-1 text-[11px] text-muted-foreground">
          The copilot interface, session store and response renderer are complete. Connect an
          inference backend to activate answers.
        </p>
      </div>
    </section>
  );
}
