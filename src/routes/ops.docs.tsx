import { createFileRoute } from "@tanstack/react-router";
import { BookOpen } from "lucide-react";
import { useState } from "react";

import { OpsSection } from "@/components/observability";
import { SYSTEM_DOCUMENTATION } from "@/data/observability-reference";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/ops/docs")({
  head: () => ({
    meta: [
      { title: "System Documentation · Velorix Sentinel" },
      {
        name: "description",
        content: "Architecture, deployment, security model and operational runbooks.",
      },
      { property: "og:title", content: "System Documentation · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Architecture, deployment, security model and operational runbooks.",
      },
    ],
  }),
  component: SystemDocumentationPage,
});

function SystemDocumentationPage() {
  const [activeId, setActiveId] = useState(SYSTEM_DOCUMENTATION[0]?.id);
  const active = SYSTEM_DOCUMENTATION.find((section) => section.id === activeId);

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,260px)_minmax(0,1fr)]">
      <OpsSection
        title="System Documentation"
        description="Reference material for platform operators."
        bodyClassName="p-2"
      >
        <ul className="space-y-1">
          {SYSTEM_DOCUMENTATION.map((section) => (
            <li key={section.id}>
              <button
                type="button"
                onClick={() => setActiveId(section.id)}
                aria-current={section.id === activeId ? "true" : undefined}
                className={cn(
                  "w-full rounded-xl px-3 py-2 text-left text-xs font-medium transition-colors",
                  section.id === activeId
                    ? "bg-primary/15 text-primary"
                    : "text-muted-foreground hover:bg-muted/40 hover:text-foreground",
                )}
              >
                {section.title}
              </button>
            </li>
          ))}
        </ul>
      </OpsSection>

      {active && (
        <OpsSection
          title={active.title}
          description={active.summary}
          actions={<BookOpen className="size-4 text-primary/80" aria-hidden="true" />}
        >
          <ul className="space-y-2">
            {active.points.map((point) => (
              <li
                key={point}
                className="flex gap-3 rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5"
              >
                <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary/70" />
                <p className="text-xs leading-relaxed text-muted-foreground">{point}</p>
              </li>
            ))}
          </ul>
        </OpsSection>
      )}
    </div>
  );
}
