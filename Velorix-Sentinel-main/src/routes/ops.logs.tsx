import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";

import { LogConsole, OpsSection } from "@/components/observability";
import { LOG_CHANNELS } from "@/data/observability-reference";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService, OBSERVABILITY_BACKEND_READY } from "@/services/observabilityService";
import { cn } from "@/lib/utils";
import type { LogChannel } from "@/types/observability";

export const Route = createFileRoute("/ops/logs")({
  head: () => ({
    meta: [
      { title: "Log Explorer · Velorix Sentinel" },
      {
        name: "description",
        content: "Unified search across application, audit, security and API log streams.",
      },
      { property: "og:title", content: "Log Explorer · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Unified search across application, audit, security and API log streams.",
      },
    ],
  }),
  component: LogExplorerPage,
});

function LogExplorerPage() {
  const [channel, setChannel] = useState<LogChannel>("APPLICATION");
  const active = LOG_CHANNELS.find((item) => item.id === channel)!;

  const logs = useObservabilityResource(
    (signal) => observabilityService.searchLogs({ channel, page: 0, size: 200 }, signal),
    [channel],
  );

  return (
    <div className="space-y-6">
      <OpsSection
        title="Log Explorer"
        description="Unified search across every log stream produced by the platform."
        actions={
          <span className="font-mono text-[10px] text-muted-foreground">
            source: {active.source}
          </span>
        }
      >
        <div className="space-y-4">
          <nav aria-label="Log channels" className="-mx-1 overflow-x-auto">
            <ul className="flex min-w-max items-center gap-1 px-1">
              {LOG_CHANNELS.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => setChannel(item.id)}
                    aria-pressed={channel === item.id}
                    className={cn(
                      "rounded-lg px-3 py-1.5 text-xs font-medium whitespace-nowrap transition-colors",
                      channel === item.id
                        ? "bg-primary/15 text-primary"
                        : "text-muted-foreground hover:bg-muted/40 hover:text-foreground",
                    )}
                  >
                    {item.label}
                  </button>
                </li>
              ))}
            </ul>
          </nav>

          <p className="text-xs text-muted-foreground">{active.description}</p>

          <LogConsole
            entries={logs.data?.content ?? []}
            isLoading={logs.status === "loading"}
            awaiting={!OBSERVABILITY_BACKEND_READY || logs.status === "awaiting"}
            channelLabel={active.label}
          />
        </div>
      </OpsSection>
    </div>
  );
}
