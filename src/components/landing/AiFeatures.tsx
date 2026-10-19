import { Bug, Clock, LineChart, MessagesSquare, Sparkles, Wrench } from "lucide-react";

import { Reveal, Section, SectionHeading } from "./Section";

const items = [
  {
    icon: Sparkles,
    title: "AI Threat Summary",
    copy: "Every capture is distilled into a plain-language incident narrative with confidence scoring.",
  },
  {
    icon: MessagesSquare,
    title: "AI Chat Assistant",
    copy: "Interrogate packets conversationally: “show me all outbound traffic to new domains”.",
  },
  {
    icon: LineChart,
    title: "Risk Prediction",
    copy: "Forecast breach likelihood per asset using traffic behaviour and exposure signals.",
  },
  {
    icon: Wrench,
    title: "Security Recommendations",
    copy: "Prioritised, actionable remediation steps mapped to your existing controls.",
  },
  {
    icon: Bug,
    title: "CVE Suggestions",
    copy: "Observed service banners and behaviours matched against relevant CVEs.",
  },
  {
    icon: Clock,
    title: "Timeline Explanation",
    copy: "Reconstructed attack chronology showing dwell time, pivots and exfiltration windows.",
  },
];

export function AiFeatures() {
  return (
    <Section className="relative">
      <div className="grid gap-12 lg:grid-cols-[0.9fr_1.1fr] lg:items-start">
        <SectionHeading
          align="left"
          eyebrow="Applied AI"
          title="An analyst copilot trained on network truth"
          description="Velorix Sentinel grounds every answer in the packets it inspected — no hallucinated verdicts, no black boxes."
        />

        <div className="grid gap-4 sm:grid-cols-2">
          {items.map((item, i) => (
            <Reveal key={item.title} delay={i * 0.05}>
              <article className="hover-glow h-full rounded-2xl border border-border bg-card/70 p-5">
                <div className="flex items-center gap-3">
                  <span className="grid size-9 place-items-center rounded-lg bg-primary/12 text-primary">
                    <item.icon className="size-4" aria-hidden="true" />
                  </span>
                  <h3 className="text-sm font-semibold">{item.title}</h3>
                </div>
                <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{item.copy}</p>
              </article>
            </Reveal>
          ))}
        </div>
      </div>
    </Section>
  );
}
