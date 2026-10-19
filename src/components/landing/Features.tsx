import { Bot, Brain, FileBarChart, Fingerprint, Gauge, Network, Radar, Waves } from "lucide-react";

import { Reveal, Section, SectionHeading } from "./Section";

const features = [
  {
    icon: Network,
    title: "Deep Packet Inspection",
    copy: "Frame-level parsing across every layer — headers, payloads, TLS metadata and application protocols.",
  },
  {
    icon: Brain,
    title: "AI Threat Detection",
    copy: "Ensemble models score each flow against known adversary tradecraft and novel anomalies.",
  },
  {
    icon: Waves,
    title: "Real-time Analysis",
    copy: "Streaming pipeline evaluates live traffic as it arrives, so detections surface while the session is still open.",
  },
  {
    icon: Radar,
    title: "Threat Intelligence",
    copy: "Continuously enriched feeds map observed indicators to active campaigns and threat actors.",
  },
  {
    icon: Gauge,
    title: "Behavior Analytics",
    copy: "Per-host and per-identity baselines surface lateral movement and privilege abuse early.",
  },
  {
    icon: Fingerprint,
    title: "IOC Extraction",
    copy: "Automatic extraction of hashes, domains, IPs and JA3 fingerprints ready for your SIEM.",
  },
  {
    icon: Bot,
    title: "AI Security Assistant",
    copy: "Ask questions about any capture in natural language and get grounded, cited answers.",
  },
  {
    icon: FileBarChart,
    title: "Automated Reports",
    copy: "Executive and technical reports generated in seconds, aligned to MITRE ATT&CK.",
  },
];

export function Features() {
  return (
    <Section id="features">
      <SectionHeading
        eyebrow="Platform"
        title="Everything a modern SOC needs in one engine"
        description="Detection, enrichment, investigation and reporting — unified on a single packet-native platform."
      />

      <div className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {features.map((f, i) => (
          <Reveal key={f.title} delay={(i % 4) * 0.06}>
            <article className="hover-glow group h-full rounded-2xl border border-border bg-card/70 p-6">
              <span className="mb-4 grid size-11 place-items-center rounded-xl border border-primary/25 bg-primary/10 text-primary transition-transform duration-300 group-hover:scale-110">
                <f.icon className="size-5" aria-hidden="true" />
              </span>
              <h3 className="text-base font-semibold">{f.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{f.copy}</p>
            </article>
          </Reveal>
        ))}
      </div>
    </Section>
  );
}
