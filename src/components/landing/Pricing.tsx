import { Check, Sparkles } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { Reveal, Section, SectionHeading } from "./Section";

const tiers = [
  {
    name: "Starter",
    price: "Free Beta",
    cadence: "",
    blurb: "For security analysts evaluating packet captures and inspecting detections.",
    cta: "Get started",
    features: [
      "Up to 500 MB per-user capture quota",
      "13-rule deterministic threat detection engine",
      "Interactive frame-level packet inspection",
      "Correlated risk scoring & evidence citations",
      "Self-service account lifecycle & data deletion",
    ],
  },
  {
    name: "Professional",
    price: "Contact sales",
    cadence: "",
    blurb: "For growing security teams requiring expanded capture quotas and programmatic access.",
    cta: "Talk to sales",
    featured: true,
    features: [
      "Expanded multi-gigabyte capture quotas",
      "Export findings via standard JSON REST APIs",
      "Long-term capture retention & audit trails",
      "SIEM webhook forwarding (planned)",
      "Standard priority email support",
    ],
  },
  {
    name: "Enterprise",
    price: "Contact sales",
    cadence: "",
    blurb: "For organizations requiring custom storage policies and dedicated infrastructure.",
    cta: "Talk to sales",
    features: [
      "Custom capture quotas and retention rules",
      "Dedicated object storage bucket configuration",
      "Custom detection rule authoring support",
      "Role-based access control & workspace isolation",
      "Dedicated support with custom SLA",
    ],
  },
];

export function Pricing() {
  return (
    <Section id="pricing">
      <SectionHeading
        eyebrow="Pricing"
        title="Plans that scale with your traffic"
        description="Every plan includes deep packet inspection, automated threat detection, and comprehensive traffic analytics."
      />

      <div className="mt-14 grid gap-5 lg:grid-cols-3">
        {tiers.map((tier, i) => (
          <Reveal key={tier.name} delay={i * 0.08}>
            <article
              className={cn(
                "relative flex h-full flex-col rounded-2xl border bg-card/70 p-7 transition-all duration-300",
                tier.featured
                  ? "border-primary/50 glow-ring"
                  : "border-border hover:-translate-y-1 hover:border-primary/40",
              )}
            >
              {tier.featured && (
                <span className="absolute -top-3 left-7 inline-flex items-center gap-1.5 rounded-full bg-gradient-brand px-3 py-1 text-[11px] font-medium text-primary-foreground">
                  <Sparkles className="size-3" /> Most popular
                </span>
              )}
              <h3 className="font-display text-lg font-semibold">{tier.name}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{tier.blurb}</p>
              <p className="mt-6 flex items-end gap-1">
                <span className="font-display text-2xl font-semibold">{tier.price}</span>
                <span className="pb-1 text-sm text-muted-foreground">{tier.cadence}</span>
              </p>
              <ul className="mt-6 flex-1 space-y-3">
                {tier.features.map((f) => (
                  <li key={f} className="flex items-start gap-2.5 text-sm text-muted-foreground">
                    <Check className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
                    {f}
                  </li>
                ))}
              </ul>
              <Button
                className="mt-8 w-full rounded-full"
                variant={tier.featured ? "default" : "outline"}
              >
                {tier.cta}
              </Button>
            </article>
          </Reveal>
        ))}
      </div>
    </Section>
  );
}
