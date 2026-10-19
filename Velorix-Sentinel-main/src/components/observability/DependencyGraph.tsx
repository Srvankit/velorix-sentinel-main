import { useState } from "react";

import { cn } from "@/lib/utils";
import type { ServiceDependencyEdge, ServiceDependencyNode } from "@/types/observability";

import { HealthBadge } from "./OpsBadges";

const LAYER_ORDER: ServiceDependencyNode["layer"][] = [
  "EDGE",
  "APPLICATION",
  "PLATFORM",
  "DATA",
  "EXTERNAL",
];

const LAYER_LABEL: Record<ServiceDependencyNode["layer"], string> = {
  EDGE: "Edge & Presentation",
  APPLICATION: "Application Services",
  PLATFORM: "Platform Services",
  DATA: "Data Tier",
  EXTERNAL: "External Providers",
};

interface DependencyGraphProps {
  nodes: ServiceDependencyNode[];
  edges: ServiceDependencyEdge[];
  className?: string;
}

/**
 * Interactive layered architecture map. Selecting a node highlights its
 * upstream and downstream relationships — no live traffic data is implied.
 */
export function DependencyGraph({ nodes, edges, className }: DependencyGraphProps) {
  const [selected, setSelected] = useState<string | null>(null);

  const related = new Set<string>();
  if (selected) {
    edges.forEach((edge) => {
      if (edge.from === selected) related.add(edge.to);
      if (edge.to === selected) related.add(edge.from);
    });
  }

  const active = nodes.find((node) => node.id === selected) ?? null;
  const upstream = selected ? edges.filter((edge) => edge.to === selected) : [];
  const downstream = selected ? edges.filter((edge) => edge.from === selected) : [];
  const label = (id: string) => nodes.find((node) => node.id === id)?.label ?? id;

  return (
    <div className={cn("grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]", className)}>
      <div className="space-y-4">
        {LAYER_ORDER.filter((layer) => nodes.some((node) => node.layer === layer)).map((layer) => (
          <div key={layer}>
            <p className="mb-2 text-[11px] tracking-wide text-muted-foreground uppercase">
              {LAYER_LABEL[layer]}
            </p>
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {nodes
                .filter((node) => node.layer === layer)
                .map((node) => {
                  const isSelected = node.id === selected;
                  const isRelated = related.has(node.id);
                  return (
                    <button
                      key={node.id}
                      type="button"
                      onClick={() => setSelected(isSelected ? null : node.id)}
                      aria-pressed={isSelected}
                      className={cn(
                        "rounded-xl border p-3 text-left transition-all",
                        isSelected
                          ? "border-primary/50 bg-primary/10 shadow-[0_0_0_1px_var(--color-primary)]/20"
                          : isRelated
                            ? "border-primary/30 bg-primary/5"
                            : "border-border/70 bg-card/40 hover:border-primary/30",
                        selected && !isSelected && !isRelated && "opacity-50",
                      )}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <span className="truncate text-xs font-semibold">{node.label}</span>
                      </div>
                      <p className="mt-1 text-[11px] text-muted-foreground">{node.detail}</p>
                      <div className="mt-2">
                        <HealthBadge state={node.state} />
                      </div>
                    </button>
                  );
                })}
            </div>
          </div>
        ))}
      </div>

      <aside className="rounded-2xl border border-border/70 bg-card/40 p-4 backdrop-blur-xl">
        <h3 className="text-xs font-semibold">{active ? active.label : "Select a component"}</h3>
        <p className="mt-1 text-[11px] text-muted-foreground">
          {active
            ? active.detail
            : "Choose any component to inspect its upstream and downstream dependencies."}
        </p>

        {active && (
          <div className="mt-4 space-y-4">
            <div>
              <p className="text-[11px] tracking-wide text-muted-foreground uppercase">
                Depends on
              </p>
              {downstream.length ? (
                <ul className="mt-1.5 space-y-1">
                  {downstream.map((edge) => (
                    <li
                      key={`${edge.from}-${edge.to}`}
                      className="flex items-center justify-between gap-2 rounded-lg border border-border/60 bg-muted/10 px-2.5 py-1.5"
                    >
                      <span className="truncate text-[11px]">{label(edge.to)}</span>
                      <span className="font-mono text-[10px] text-muted-foreground">
                        {edge.protocol}
                        {edge.critical ? " · critical" : ""}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-1.5 text-[11px] text-muted-foreground">No downstream services.</p>
              )}
            </div>

            <div>
              <p className="text-[11px] tracking-wide text-muted-foreground uppercase">
                Consumed by
              </p>
              {upstream.length ? (
                <ul className="mt-1.5 space-y-1">
                  {upstream.map((edge) => (
                    <li
                      key={`${edge.from}-${edge.to}`}
                      className="flex items-center justify-between gap-2 rounded-lg border border-border/60 bg-muted/10 px-2.5 py-1.5"
                    >
                      <span className="truncate text-[11px]">{label(edge.from)}</span>
                      <span className="font-mono text-[10px] text-muted-foreground">
                        {edge.protocol}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-1.5 text-[11px] text-muted-foreground">No upstream consumers.</p>
              )}
            </div>

            <p className="rounded-lg border border-dashed border-border/70 bg-muted/10 px-2.5 py-2 text-[11px] text-muted-foreground">
              Live health, saturation and error propagation per edge arrive with distributed
              tracing. Awaiting Live Platform Monitoring.
            </p>
          </div>
        )}
      </aside>
    </div>
  );
}
