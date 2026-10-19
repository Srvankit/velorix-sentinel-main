import { Link } from "@tanstack/react-router";
import type { LucideIcon } from "lucide-react";
import {
  BookOpen,
  FileBarChart,
  Layers,
  Lock,
  Radar,
  ScrollText,
  Settings,
  UploadCloud,
  Zap,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ROUTES } from "@/utils/constants";

const DISABLED_REASON = "Available after upload engine is completed.";

interface ActionDef {
  label: string;
  description: string;
  icon: LucideIcon;
  to?: (typeof ROUTES)[keyof typeof ROUTES];
  href?: string;
  disabled?: boolean;
}

const ACTIONS: ActionDef[] = [
  {
    label: "Upload capture",
    description: DISABLED_REASON,
    icon: UploadCloud,
    disabled: true,
  },
  {
    label: "Threat intelligence",
    description: "Review detections and triage findings",
    icon: Radar,
    to: ROUTES.threats,
  },
  {
    label: "Generate report",
    description: "Export an executive security summary",
    icon: FileBarChart,
    to: ROUTES.reports,
  },
  {
    label: "View investigations",
    description: "Open the analysis workspace",
    icon: Layers,
    to: ROUTES.analysis,
  },
  {
    label: "Settings",
    description: "Workspace, security and appearance",
    icon: Settings,
    to: ROUTES.settings,
  },
  {
    label: "Documentation",
    description: "API reference and analyst playbooks",
    icon: BookOpen,
    href: "https://swagger.io/",
  },
  {
    label: "System logs",
    description: "Audit trail of platform events",
    icon: ScrollText,
    to: ROUTES.history,
  },
];

function ActionTile({ action }: { action: ActionDef }) {
  const body = (
    <>
      <span
        className={cn(
          "grid size-9 shrink-0 place-items-center rounded-lg transition-colors",
          action.disabled
            ? "bg-muted text-muted-foreground"
            : "bg-primary/10 text-primary group-hover:bg-primary/20",
        )}
      >
        <action.icon className="size-4" aria-hidden="true" />
      </span>
      <span className="min-w-0">
        <span className="flex items-center gap-1.5 truncate text-sm font-medium">
          {action.label}
          {action.disabled && <Lock className="size-3 shrink-0 text-muted-foreground" />}
        </span>
        <span className="block truncate text-xs text-muted-foreground">{action.description}</span>
      </span>
    </>
  );

  const shell = cn(
    "group grid grid-cols-[auto_minmax(0,1fr)] items-center gap-3 rounded-xl border border-border/70 bg-surface/40 p-3 text-left transition-all",
    action.disabled
      ? "cursor-not-allowed opacity-60"
      : "hover:-translate-y-0.5 hover:border-primary/30 hover:bg-surface/70",
  );

  if (action.disabled) {
    return (
      <div className={shell} aria-disabled="true" title={DISABLED_REASON}>
        {body}
      </div>
    );
  }

  if (action.href) {
    return (
      <a className={shell} href={action.href} target="_blank" rel="noreferrer noopener">
        {body}
      </a>
    );
  }

  return (
    <Link className={shell} to={action.to!}>
      {body}
    </Link>
  );
}

/** Command center of one-click analyst actions. */
export function QuickActions() {
  return (
    <section className="glass-panel rounded-2xl p-5">
      <header className="mb-4 grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2">
        <div className="flex min-w-0 items-center gap-2">
          <Zap className="size-4 text-primary" aria-hidden="true" />
          <h2 className="truncate text-sm font-semibold tracking-tight">Quick actions</h2>
        </div>
        <Button variant="ghost" size="sm" asChild>
          <Link to="/dashboard">Command center</Link>
        </Button>
      </header>
      <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
        {ACTIONS.map((action) => (
          <ActionTile key={action.label} action={action} />
        ))}
      </div>
    </section>
  );
}
