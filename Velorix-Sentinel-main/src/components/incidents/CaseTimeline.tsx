import {
  CheckCircle2,
  FileSearch,
  FlaskConical,
  MessageSquare,
  ShieldPlus,
  UserPlus,
  Workflow,
  Zap,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import type { IncidentEvent, IncidentEventKind } from "@/types/incident";

const KIND_META: Record<IncidentEventKind, { icon: LucideIcon; tone: string }> = {
  CASE_CREATED: { icon: ShieldPlus, tone: "border-primary/40 bg-primary/10 text-primary" },
  ANALYST_ASSIGNED: { icon: UserPlus, tone: "border-sky-500/40 bg-sky-500/10 text-sky-400" },
  STATUS_CHANGED: { icon: Workflow, tone: "border-amber-500/40 bg-amber-500/10 text-amber-400" },
  SEVERITY_CHANGED: { icon: Zap, tone: "border-orange-500/40 bg-orange-500/10 text-orange-400" },
  EVIDENCE_ADDED: {
    icon: FileSearch,
    tone: "border-violet-500/40 bg-violet-500/10 text-violet-300",
  },
  COMMENT_ADDED: { icon: MessageSquare, tone: "border-border bg-muted/40 text-muted-foreground" },
  PLAYBOOK_EXECUTED: {
    icon: FlaskConical,
    tone: "border-teal-500/40 bg-teal-500/10 text-teal-300",
  },
  CONTAINMENT_ACTION: { icon: Zap, tone: "border-primary/40 bg-primary/10 text-primary" },
  RESOLUTION: {
    icon: CheckCircle2,
    tone: "border-emerald-500/40 bg-emerald-500/10 text-emerald-400",
  },
};

interface CaseTimelineProps {
  events: IncidentEvent[];
  className?: string;
}

/**
 * Investigation timeline. Renders whatever the backend returns for
 * `/v1/incidents/{id}/timeline` — case created, analyst assigned, status
 * updates, evidence, comments and resolution.
 */
export function CaseTimeline({ events, className }: CaseTimelineProps) {
  return (
    <ol className={cn("relative space-y-5 ps-8", className)}>
      <span className="absolute inset-y-2 left-[15px] w-px bg-border" aria-hidden="true" />
      {events.map((event, index) => {
        const meta = KIND_META[event.kind] ?? KIND_META.COMMENT_ADDED;
        const Icon = meta.icon;
        return (
          <li
            key={event.id}
            className="relative animate-[fade-in_0.4s_ease-out_both]"
            style={{ animationDelay: `${index * 60}ms` }}
          >
            <span
              className={cn(
                "absolute top-0 -left-8 grid size-8 place-items-center rounded-full border",
                meta.tone,
              )}
              aria-hidden="true"
            >
              <Icon className="size-3.5" />
            </span>
            <div className="rounded-xl border border-border/70 bg-card/40 p-3 transition-colors hover:border-primary/30">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-medium text-foreground">{event.summary}</p>
                <time className="font-mono text-[11px] text-muted-foreground">
                  {new Date(event.occurredAt).toLocaleString()}
                </time>
              </div>
              {event.detail && (
                <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{event.detail}</p>
              )}
              {event.actor && (
                <p className="mt-1.5 text-[11px] text-muted-foreground">
                  {event.actor.displayName} · {event.actor.role}
                </p>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
