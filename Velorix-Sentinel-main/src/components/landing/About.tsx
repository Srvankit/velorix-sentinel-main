import { Building2, Compass, ShieldCheck } from "lucide-react";

import { APP_NAME, COMPANY_NAME } from "@/utils/constants";
import { Reveal, Section, SectionHeading } from "./Section";

const pillars = [
  {
    icon: Building2,
    title: "The parent company",
    body: `${COMPANY_NAME} builds engineering-led products for teams that operate critical infrastructure.`,
  },
  {
    icon: ShieldCheck,
    title: "The flagship product",
    body: `${APP_NAME} is our cybersecurity platform: deep packet inspection paired with AI-assisted investigation.`,
  },
  {
    icon: Compass,
    title: "How we work",
    body: "Evidence over assertion. Every detection points back to the packets that produced it, so analysts can verify the conclusion.",
  },
];

export function About() {
  return (
    <Section id="about">
      <SectionHeading
        eyebrow="About"
        title={`${APP_NAME}, a ${COMPANY_NAME} product`}
        description="Built for security teams who need packet-level truth, not dashboards full of estimates."
      />

      <div className="mt-14 grid gap-4 lg:grid-cols-3">
        {pillars.map((p, i) => (
          <Reveal key={p.title} delay={i * 0.08}>
            <article className="hover-glow flex h-full flex-col gap-4 rounded-2xl border border-border bg-card/70 p-6">
              <span className="grid size-10 place-items-center rounded-xl border border-primary/30 bg-primary/10 text-primary">
                <p.icon className="size-5" aria-hidden="true" />
              </span>
              <h3 className="font-display text-base font-semibold">{p.title}</h3>
              <p className="text-sm leading-relaxed text-muted-foreground">{p.body}</p>
            </article>
          </Reveal>
        ))}
      </div>
    </Section>
  );
}
