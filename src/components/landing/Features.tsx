import { Database, FileBarChart, Fingerprint, Gauge, Network, Radar, ShieldAlert, Waves } from "lucide-react";

import { Reveal, Section, SectionHeading } from "./Section";

const features = [
  {
    icon: Network,
    title: "Deep Packet Inspection",
    copy: "Frame-level decoding across Ethernet, IPv4, IPv6, TCP, UDP, ICMP, DNS, and HTTP layers.",
  },
  {
    icon: ShieldAlert,
    title: "Rule-Based Threat Detection",
    copy: "High-performance deterministic rule engine detecting scans, floods, brute-force, beaconing, and anomalous flows across 13 rules.",
  },
  {
    icon: Waves,
    title: "Streaming Capture Pipeline",
    copy: "Non-blocking streaming pipeline processing large PCAP and PCAPNG captures with automatic stuck-run recovery.",
  },
  {
    icon: Radar,
    title: "Threat Intelligence & MITRE",
    copy: "Observed findings correlated against MITRE ATT&CK tactics, threat categories, and severity tiers.",
  },
  {
    icon: Gauge,
    title: "Traffic Analytics & Profiling",
    copy: "Per-capture bandwidth metrics, packet counts, protocol breakdown, and top-communicating endpoints.",
  },
  {
    icon: Fingerprint,
    title: "Indicator & Artifact Extraction",
    copy: "Automatic extraction and indexing of source and destination IPs, ports, MAC addresses, and packet headers.",
  },
  {
    icon: FileBarChart,
    title: "Transparent Risk Scoring",
    copy: "Normalized 0–100 risk scoring with evidence citation linking every finding directly to specific packets.",
  },
  {
    icon: Database,
    title: "Secure Storage & Lifecycle",
    copy: "Private cloud object storage with per-user capture quotas, email verification guards, and self-service data deletion.",
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
