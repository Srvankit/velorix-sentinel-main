import { createFileRoute } from "@tanstack/react-router";

import { DependencyGraph, OpsSection } from "@/components/observability";
import { DEPENDENCY_EDGES, DEPENDENCY_NODES } from "@/data/observability-reference";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService } from "@/services/observabilityService";

export const Route = createFileRoute("/ops/dependencies")({
  head: () => ({
    meta: [
      { title: "Service Dependencies · Velorix Sentinel" },
      {
        name: "description",
        content: "Interactive map of platform services and their critical dependencies.",
      },
      { property: "og:title", content: "Service Dependencies · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Interactive map of platform services and their critical dependencies.",
      },
    ],
  }),
  component: DependenciesPage,
});

function DependenciesPage() {
  const graph = useObservabilityResource(observabilityService.getDependencyGraph);

  const nodes = graph.data?.nodes?.length ? graph.data.nodes : DEPENDENCY_NODES;
  const edges = graph.data?.edges?.length ? graph.data.edges : DEPENDENCY_EDGES;

  return (
    <div className="space-y-6">
      <OpsSection
        title="Service Dependency Map"
        description="How every module of the platform connects, from browser to data tier. Select a component to trace its relationships."
      >
        <DependencyGraph nodes={nodes} edges={edges} />
      </OpsSection>

      <OpsSection
        title="Critical Path"
        description="Dependencies that make the platform unavailable when they fail."
      >
        <ul className="grid gap-2 md:grid-cols-2">
          {edges
            .filter((edge) => edge.critical)
            .map((edge) => {
              const label = (id: string) => nodes.find((node) => node.id === id)?.label ?? id;
              return (
                <li
                  key={`${edge.from}-${edge.to}`}
                  className="flex items-center justify-between gap-2 rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5"
                >
                  <span className="truncate text-xs">
                    {label(edge.from)} <span className="text-muted-foreground">→</span>{" "}
                    {label(edge.to)}
                  </span>
                  <span className="font-mono text-[10px] text-muted-foreground">
                    {edge.protocol}
                  </span>
                </li>
              );
            })}
        </ul>
      </OpsSection>
    </div>
  );
}
