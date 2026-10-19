import { createFileRoute } from "@tanstack/react-router";
import { BookOpenCheck, ChevronRight } from "lucide-react";
import { useState } from "react";

import { IncidentCard, SeverityChip } from "@/components/incidents";
import { PageHeader } from "@/components/common";
import { PHASE_LABELS, PHASE_ORDER, playbooks } from "@/data/playbooks";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/incidents/playbooks")({
  head: () => ({
    meta: [
      { title: "Response Playbooks · Velorix Sentinel" },
      {
        name: "description",
        content: "NIST-aligned incident response playbooks for containment and recovery.",
      },
      { property: "og:title", content: "Response Playbooks · Velorix Sentinel" },
      {
        property: "og:description",
        content: "NIST-aligned incident response playbooks for containment and recovery.",
      },
    ],
  }),
  component: PlaybooksPage,
});

function PlaybooksPage() {
  const [activeId, setActiveId] = useState(playbooks[0]?.id ?? "");
  const active = playbooks.find((playbook) => playbook.id === activeId) ?? playbooks[0];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Security Playbooks"
        description="Standard response doctrine following the NIST SP 800-61r2 incident lifecycle."
      />

      <div className="grid gap-4 lg:grid-cols-[280px_1fr]">
        <nav aria-label="Playbooks" className="space-y-2">
          {playbooks.map((playbook) => (
            <button
              key={playbook.id}
              type="button"
              onClick={() => setActiveId(playbook.id)}
              aria-current={playbook.id === active?.id ? "true" : undefined}
              className={cn(
                "focus-ring flex w-full items-center justify-between gap-3 rounded-xl border p-3 text-left transition-colors",
                playbook.id === active?.id
                  ? "border-primary/40 bg-primary/10"
                  : "border-border bg-card/40 hover:border-primary/30",
              )}
            >
              <span className="min-w-0">
                <span className="block text-sm font-medium">{playbook.name}</span>
                <span className="block text-[11px] text-muted-foreground">
                  {playbook.estimatedDuration}
                </span>
              </span>
              <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            </button>
          ))}
        </nav>

        {active && (
          <IncidentCard
            title={active.name}
            description={active.summary}
            icon={BookOpenCheck}
            badge={<SeverityChip severity={active.severityFocus} />}
          >
            <div className="flex flex-wrap gap-1.5">
              {active.mitreTactics.map((tactic) => (
                <span
                  key={tactic}
                  className="rounded-full border border-border bg-muted/40 px-2 py-0.5 text-[11px] text-muted-foreground"
                >
                  {tactic}
                </span>
              ))}
            </div>

            <ol className="space-y-3">
              {PHASE_ORDER.map((phaseId, index) => {
                const phase = active.phases.find((item) => item.id === phaseId);
                if (!phase) return null;
                return (
                  <li key={phase.id} className="rounded-xl border border-border/70 bg-card/40 p-4">
                    <div className="flex items-center gap-3">
                      <span className="grid size-7 shrink-0 place-items-center rounded-full bg-primary/10 text-xs font-semibold text-primary">
                        {index + 1}
                      </span>
                      <div>
                        <h3 className="text-sm font-semibold">{PHASE_LABELS[phase.id]}</h3>
                        <p className="text-[11px] text-muted-foreground">{phase.objective}</p>
                      </div>
                    </div>
                    <ul className="mt-3 space-y-1.5 ps-10">
                      {phase.steps.map((step) => (
                        <li
                          key={step}
                          className="relative text-sm text-muted-foreground before:absolute before:-left-4 before:top-2 before:size-1.5 before:rounded-full before:bg-primary/50"
                        >
                          {step}
                        </li>
                      ))}
                    </ul>
                  </li>
                );
              })}
            </ol>
          </IncidentCard>
        )}
      </div>
    </div>
  );
}
