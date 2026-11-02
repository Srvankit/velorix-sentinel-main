import { Bug, Clock, LineChart, MessagesSquare, Sparkles, Wrench } from "lucide-react";

import { Reveal, Section, SectionHeading } from "./Section";

const items = [
  {
    icon: Sparkles,
    title: "AI Threat Summary (Roadmap)",
    copy: "Planned post-beta capability to distill inspected captures into plain-language incident narratives with evidence links.",
    badge: "Roadmap",
  },
  {
    icon: MessagesSquare,
    title: "Conversational Query Assistant (Roadmap)",
    copy: "Future natural language interface to interrogate packet records conversationally without manual query syntax.",
    badge: "Roadmap",
  },
  {
    icon: LineChart,
    title: "Risk & Exposure Prediction (Roadmap)",
    copy: "Forecast asset breach likelihood using observed traffic behavior, lateral movement signals, and anomaly metrics.",
    badge: "Roadmap",
  },
  {
    icon: Wrench,
    title: "Prescriptive Remediation (Roadmap)",
    copy: "Context-aware containment guidance and mitigation steps mapped to observed threat signatures.",
    badge: "Roadmap",
  },
  {
    icon: Bug,
    title: "CVE & Exploit Mapping (Roadmap)",
    copy: "Correlate observed service banners and network traffic patterns with known CVE databases.",
    badge: "Roadmap",
  },
  {
    icon: Clock,
    title: "Attack Timeline Explanation (Roadmap)",
    copy: "Reconstruct attack chronology showing dwell time, pivot windows, and anomalous exfiltration channels.",
    badge: "Roadmap",
  },
];

export function AiFeatures() {
  return (
    <Section className="relative">
      <div className="grid gap-12 lg:grid-cols-[0.9fr_1.1fr] lg:items-start">
        <SectionHeading
          align="left"
          eyebrow="Roadmap Capabilities"
          title="AI-Assisted Threat Intelligence (Roadmap)"
          description="Planned post-beta LLM integrations (Google Gemini / OpenAI API) to augment verified packet traces with automated incident narratives, conversational queries, and remediation roadmaps. No AI inference is simulated."
        />

        <div className="grid gap-4 sm:grid-cols-2">
          {items.map((item, i) => (
            <Reveal key={item.title} delay={i * 0.05}>
              <article className="hover-glow h-full rounded-2xl border border-border bg-card/70 p-5">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-3">
                    <span className="grid size-9 place-items-center rounded-lg bg-primary/12 text-primary">
                      <item.icon className="size-4" aria-hidden="true" />
                    </span>
                    <h3 className="text-sm font-semibold">{item.title}</h3>
                  </div>
                  <span className="rounded-full border border-warning/40 bg-warning/10 px-2 py-0.5 font-mono text-[10px] font-medium uppercase tracking-wider text-warning">
                    {item.badge}
                  </span>
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
