import { Check, Sparkles } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { Reveal, Section, SectionHeading } from "./Section";

const tiers = [
  {
    name: "Starter",
    price: "Contact sales",
    cadence: "",
    blurb: "For lean teams securing a single site or product network.",
    cta: "Talk to sales",
    features: [
      "Up to 50 GB of captures / month",
      "Core detection rule set",
      "AI threat summaries",
      "7-day retention",
      "Email support",
    ],
  },
  {
    name: "Professional",
    price: "Contact sales",
    cadence: "",
    blurb: "For growing SOCs that need continuous inspection and AI triage.",
    cta: "Talk to sales",
    featured: true,
    features: [
      "Up to 1 TB of captures / month",
      "Behaviour analytics & baselines",
      "AI chat assistant + CVE mapping",
      "90-day retention",
      "SIEM & SOAR integrations",
      "24/5 priority support",
    ],
  },
  {
    name: "Enterprise",
    price: "Contact sales",
    cadence: "",
    blurb: "For regulated, multi-region estates with bespoke controls.",
    cta: "Talk to sales",
    features: [
      "Unlimited throughput",
      "On-prem or private cloud deploy",
      "Custom detection engineering",
      "Unlimited retention & audit logs",
      "SSO, SCIM, RBAC & data residency",
      "24/7 support with named TAM",
    ],
  },
];

export function Pricing() {
  return (
    <Section id="pricing">
      <SectionHeading
        eyebrow="Pricing"
        title="Plans that scale with your traffic"
        description="Every plan includes deep packet inspection, AI analysis and automated reporting. Pricing is quoted against your traffic volume."
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
