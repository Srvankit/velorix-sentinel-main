import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";

import { PageHeader } from "@/components/common";
import { IocWorkspace } from "@/components/intel";
import { IOC_CATEGORIES } from "@/data/ioc-catalog";
import type { IocRecord } from "@/data/ioc-catalog";
import { threatIntelService } from "@/services/threatIntelService";
import { useIntelResource } from "@/hooks/useIntelResource";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/intel/iocs")({
  head: () => ({
    meta: [
      { title: "Indicators of Compromise · Velorix Sentinel" },
      {
        name: "description",
        content: "Manage IP, domain, hash and URL indicators with confidence scoring.",
      },
      { property: "og:title", content: "Indicators of Compromise · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Manage IP, domain, hash and URL indicators with confidence scoring.",
      },
    ],
  }),
  component: IocPage,
});

function IocPage() {
  const [active, setActive] = useState(IOC_CATEGORIES[0].kind);
  const category = IOC_CATEGORIES.find((item) => item.kind === active) ?? IOC_CATEGORIES[0];

  const state = useIntelResource(
    (signal) => threatIntelService.listIocs({ kind: active }, signal),
    [active],
  );

  const records: IocRecord[] = state.status === "ready" ? state.data.content : [];

  return (
    <>
      <PageHeader
        title="IOC Management"
        description="Curate, enrich and operationalise indicators of compromise across nine indicator classes."
      />

      <div className="mt-6 grid gap-6 lg:grid-cols-[240px_1fr]">
        <nav
          aria-label="Indicator classes"
          className="flex gap-2 overflow-x-auto lg:flex-col lg:overflow-visible"
        >
          {IOC_CATEGORIES.map((item) => (
            <button
              key={item.kind}
              type="button"
              onClick={() => setActive(item.kind)}
              aria-pressed={item.kind === active}
              className={cn(
                "focus-ring flex shrink-0 items-center gap-2 rounded-xl border px-3 py-2 text-xs transition-colors lg:w-full",
                item.kind === active
                  ? "border-primary/40 bg-primary/10 text-primary"
                  : "border-border text-muted-foreground hover:border-primary/25 hover:text-foreground",
              )}
            >
              <item.icon className="size-3.5" aria-hidden="true" />
              {item.plural}
            </button>
          ))}
        </nav>

        <div className="glass-panel p-5">
          <IocWorkspace
            category={category}
            records={records}
            isLoading={state.status === "loading"}
            awaiting={state.status === "awaiting"}
          />
        </div>
      </div>
    </>
  );
}
