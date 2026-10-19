import { Cloud, Database, GitBranch, Globe, KeyRound, Layers, Timer, Zap } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import { apiConfig } from "@/utils/apiConfig";
import { useSystemHealth } from "@/hooks/useDashboard";

interface StatusRow {
  icon: LucideIcon;
  label: string;
  value: string;
  tone?: "success" | "warning" | "danger" | "muted";
}

/** Operations center: where the platform runs and how it is configured. */
export function PlatformStatus() {
  const { data, isError } = useSystemHealth();

  let host = "Not configured";
  try {
    host = new URL(apiConfig.baseUrl, "http://localhost").host;
  } catch {
    host = apiConfig.baseUrl;
  }

  const rows: StatusRow[] = [
    {
      icon: Cloud,
      label: "Render",
      value: isError ? "Unreachable" : data ? "Operational" : "Checking…",
      tone: isError ? "danger" : data ? "success" : "muted",
    },
    {
      icon: Database,
      label: "Neon database",
      value: data ? data.database.status : "Checking…",
      tone: data?.database.status === "UP" ? "success" : data ? "warning" : "muted",
    },
    {
      icon: KeyRound,
      label: "Authentication",
      value: "JWT · access + refresh",
      tone: "success",
    },
    {
      icon: Layers,
      label: "Environment",
      value: import.meta.env.MODE === "production" ? "Production" : "Preview",
    },
    { icon: GitBranch, label: "Deployment", value: host },
    { icon: Zap, label: "Version", value: "v1.0.0" },
    { icon: Globe, label: "Region", value: "Auto (edge)" },
    { icon: Timer, label: "Response time", value: "Awaiting Data", tone: "muted" },
  ];

  const TONE: Record<NonNullable<StatusRow["tone"]>, string> = {
    success: "text-success",
    warning: "text-warning",
    danger: "text-destructive",
    muted: "text-muted-foreground",
  };

  return (
    <section className="glass-panel rounded-2xl p-5">
      <header className="mb-4 flex items-center gap-2">
        <Cloud className="size-4 text-primary" aria-hidden="true" />
        <h2 className="text-sm font-semibold tracking-tight">Operations center</h2>
      </header>
      <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2 xl:grid-cols-1">
        {rows.map((row) => (
          <div
            key={row.label}
            className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-2 border-b border-border/50 pb-2 last:border-0 last:pb-0"
          >
            <row.icon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
            <dt className="truncate text-xs text-muted-foreground">{row.label}</dt>
            <dd
              className={cn(
                "truncate text-right font-mono text-xs font-medium",
                row.tone ? TONE[row.tone] : "text-foreground",
              )}
              title={row.value}
            >
              {row.value}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
