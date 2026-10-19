import { Link } from "@tanstack/react-router";
import {
  BrainCircuit,
  Crosshair,
  FileBarChart,
  FlaskConical,
  ShieldPlus,
  UserPlus,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { toast } from "sonner";

import { cn } from "@/lib/utils";

interface CommandAction {
  id: string;
  label: string;
  description: string;
  icon: LucideIcon;
  /** Navigates instead of requiring the backend. */
  to?: "/intel" | "/incidents/playbooks" | "/incidents/reports";
}

const ACTIONS: CommandAction[] = [
  {
    id: "create-case",
    label: "Create Case",
    description: "Open a new investigation record",
    icon: ShieldPlus,
  },
  {
    id: "assign",
    label: "Assign Incident",
    description: "Route a case to an analyst",
    icon: UserPlus,
  },
  {
    id: "ai-summary",
    label: "Generate AI Summary",
    description: "Summarise the investigation",
    icon: BrainCircuit,
  },
  {
    id: "run-playbook",
    label: "Run Playbook",
    description: "Execute a response procedure",
    icon: FlaskConical,
    to: "/incidents/playbooks",
  },
  {
    id: "open-intel",
    label: "Open Threat Intelligence",
    description: "Pivot to the intelligence centre",
    icon: Crosshair,
    to: "/intel",
  },
  {
    id: "generate-report",
    label: "Generate Report",
    description: "Build the incident report",
    icon: FileBarChart,
    to: "/incidents/reports",
  },
];

const UNAVAILABLE = "Available after backend integration.";

/** Quick-action grid. Backend-dependent actions announce their unavailability. */
export function CommandCenter({ className }: { className?: string }) {
  return (
    <div className={cn("grid gap-3 sm:grid-cols-2 xl:grid-cols-3", className)}>
      {ACTIONS.map((action) => {
        const content = (
          <>
            <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary transition-transform duration-200 group-hover:scale-110">
              <action.icon className="size-4" aria-hidden="true" />
            </span>
            <span className="min-w-0">
              <span className="block text-sm font-medium text-foreground">{action.label}</span>
              <span className="block truncate text-xs text-muted-foreground">
                {action.description}
              </span>
            </span>
          </>
        );

        const shell =
          "focus-ring group flex items-center gap-3 rounded-xl border border-border/70 bg-card/40 p-3 text-left transition-all hover:-translate-y-0.5 hover:border-primary/40 hover:bg-card/70";

        if (action.to) {
          return (
            <Link key={action.id} to={action.to} className={shell}>
              {content}
            </Link>
          );
        }

        return (
          <button
            key={action.id}
            type="button"
            onClick={() => toast.info(action.label, { description: UNAVAILABLE })}
            className={shell}
          >
            {content}
          </button>
        );
      })}
    </div>
  );
}
