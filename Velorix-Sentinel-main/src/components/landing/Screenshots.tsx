import { AlertTriangle, Filter, Search } from "lucide-react";

import { SkeletonList, SkeletonTable, SkeletonText } from "@/components/common";
import { Reveal, Section, SectionHeading } from "./Section";

function Chrome({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="glass-panel overflow-hidden p-0">
      <div className="flex items-center justify-between border-b border-border px-4 py-2.5">
        <div className="flex items-center gap-1.5">
          <span className="size-2 rounded-full bg-destructive/70" />
          <span className="size-2 rounded-full bg-warning/70" />
          <span className="size-2 rounded-full bg-success/70" />
        </div>
        <span className="font-mono text-[10px] text-muted-foreground">{title}</span>
        <span className="w-8" />
      </div>
      <div className="p-4">{children}</div>
    </div>
  );
}

export function Screenshots() {
  return (
    <Section id="documentation" className="relative">
      <SectionHeading
        eyebrow="Inside the console"
        title="Purpose-built surfaces for every stage of an investigation"
        description="Packet explorer, threat triage and AI reporting. Every surface renders from your own capture data — nothing here is simulated."
      />

      <div className="mt-14 grid gap-6 lg:grid-cols-3">
        <Reveal className="lg:col-span-2">
          <Chrome title="packet-explorer">
            <div className="mb-3 flex flex-wrap items-center gap-2">
              <span className="inline-flex items-center gap-2 rounded-lg border border-border bg-elevated/60 px-3 py-1.5 text-xs text-muted-foreground">
                <Search className="size-3.5" aria-hidden="true" /> Display filter
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-lg border border-border px-2.5 py-1.5 text-[11px] text-muted-foreground">
                <Filter className="size-3.5" aria-hidden="true" /> No capture loaded
              </span>
            </div>
            <SkeletonTable
              rows={5}
              columns={6}
              label="Packet table populates after a capture is ingested"
            />
          </Chrome>
        </Reveal>

        <Reveal delay={0.1}>
          <Chrome title="triage-queue">
            <div className="mb-3 flex items-center gap-2 text-xs text-muted-foreground">
              <AlertTriangle className="size-3.5 text-primary" aria-hidden="true" />
              Findings appear as detections fire
            </div>
            <SkeletonList items={3} label="Triage queue awaiting detections" />
          </Chrome>
        </Reveal>

        <Reveal delay={0.15} className="lg:col-span-3">
          <Chrome title="ai-report">
            <SkeletonText lines={4} />
          </Chrome>
        </Reveal>
      </div>
    </Section>
  );
}
