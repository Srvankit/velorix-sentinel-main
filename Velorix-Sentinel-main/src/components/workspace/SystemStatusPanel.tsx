import { Activity, Bot, Database, KeyRound, Radar, Server, Workflow } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { StatusPulse } from "./AlertChips";
import { useSocResource } from "@/hooks/useSocResource";
import { socService } from "@/services/socService";
import { cn } from "@/lib/utils";
import type { ComponentState, SystemComponentStatus } from "@/types/soc";

const COMPONENTS: { key: SystemComponentStatus["key"]; label: string; icon: LucideIcon }[] = [
  { key: "BACKEND", label: "Backend", icon: Server },
  { key: "DATABASE", label: "Database", icon: Database },
  { key: "AUTHENTICATION", label: "Authentication", icon: KeyRound },
  { key: "THREAT_ENGINE", label: "Threat Engine", icon: Radar },
  { key: "INSPECTION_WORKER", label: "Inspection Worker", icon: Workflow },
  { key: "AI_COPILOT", label: "AI Copilot", icon: Bot },
  { key: "THREAT_INTELLIGENCE", label: "Threat Intelligence", icon: Activity },
];

/**
 * Live system status. Component states come from `/v1/soc/status`; until that
 * endpoint ships every row reports `UNKNOWN` rather than a reassuring green.
 */
export function SystemStatusPanel({ className }: { className?: string }) {
  const status = useSocResource((signal) => socService.getSystemStatus(signal));
  const byKey = new Map((status.data ?? []).map((row) => [row.key, row]));

  return (
    <ul className={cn("space-y-1.5", className)}>
      {COMPONENTS.map((component) => {
        const live = byKey.get(component.key);
        const state: ComponentState = live?.state ?? "UNKNOWN";
        return (
          <li
            key={component.key}
            className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2 rounded-xl border border-border/70 bg-card/40 px-3 py-2"
          >
            <span className="flex min-w-0 items-center gap-2">
              <component.icon
                className="size-3.5 shrink-0 text-muted-foreground"
                aria-hidden="true"
              />
              <span className="min-w-0">
                <span className="block truncate text-xs font-medium">{component.label}</span>
                <span className="block truncate text-[10px] text-muted-foreground">
                  {live?.detail ?? "Awaiting health telemetry"}
                </span>
              </span>
            </span>
            <span className="flex shrink-0 items-center gap-2">
              {live?.latencyMs != null && (
                <span className="font-mono text-[10px] text-muted-foreground">
                  {live.latencyMs}ms
                </span>
              )}
              <StatusPulse state={state} />
            </span>
          </li>
        );
      })}
    </ul>
  );
}
