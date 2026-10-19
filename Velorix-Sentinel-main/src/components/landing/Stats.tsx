import { Activity, Gauge, ShieldAlert, Building2 } from "lucide-react";

import { SkeletonStat } from "@/components/common";
import { Reveal, Section } from "./Section";

/**
 * Platform metrics.
 *
 * No values are displayed until the backend reports them — the tiles render
 * skeleton placeholders so nothing on the page is fabricated.
 */
const metrics = [
  { label: "Threats detected", icon: ShieldAlert },
  { label: "Packets processed", icon: Activity },
  { label: "Organizations protected", icon: Building2 },
  { label: "Detection accuracy", icon: Gauge },
];

export function Stats() {
  return (
    <Section className="relative">
      <Reveal className="rounded-3xl border border-border bg-card/60 p-8 sm:p-12">
        <p className="text-center font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground">
          Platform metrics · live from your tenant
        </p>
        <dl className="mt-10 grid gap-10 sm:grid-cols-2 lg:grid-cols-4">
          {metrics.map((m) => (
            <div key={m.label} className="flex flex-col items-center gap-3 text-center">
              <m.icon className="size-5 text-primary" aria-hidden="true" />
              <dt className="sr-only">{m.label}</dt>
              <dd className="flex flex-col items-center gap-3">
                <SkeletonStat label={`${m.label} — available once your tenant is connected`} />
                <span className="text-sm text-muted-foreground">{m.label}</span>
              </dd>
            </div>
          ))}
        </dl>
        <p className="mt-10 text-center text-xs text-muted-foreground">
          Figures populate from your own telemetry after sign-in. We don&apos;t publish sample
          numbers.
        </p>
      </Reveal>
    </Section>
  );
}
