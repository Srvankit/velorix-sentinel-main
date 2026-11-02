import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Reveal, Section, SectionHeading } from "./Section";

const faqs = [
  {
    q: "How does Velorix Sentinel analyze traffic without slowing my network?",
    a: "Velorix Sentinel is optimized for offline PCAP/PCAPNG capture analysis. Inspection runs asynchronously out-of-band against uploaded captures, eliminating inline bottlenecks and adding zero latency to production traffic paths.",
  },
  {
    q: "Do you decrypt TLS traffic?",
    a: "Decryption is not required. Velorix Sentinel features a high-performance deterministic rule engine detecting scans, floods, brute-force, beaconing, and anomalous flows using protocol metadata, packet timing, volume, and connection characteristics.",
  },
  {
    q: "Where is my capture data stored?",
    a: "Captures are stored in secure, private cloud object storage with strict tenant-isolation and owner-scoped access control. Binaries are validated, stored with unique cryptographic references, and accessible only to authenticated owners.",
  },
  {
    q: "How accurate are the threat detections?",
    a: "Detections are driven by a high-performance deterministic rule engine detecting scans, floods, brute-force, beaconing, and anomalous flows across 13 specialized security rules. Every finding cites the exact packets and frames that triggered it.",
  },
  {
    q: "Which tools does Velorix Sentinel integrate with?",
    a: "Export findings via standard JSON REST APIs (SIEM webhooks planned). Analysts can query findings, summaries, and packet-level details programmatically using standard authenticated API endpoints.",
  },
  {
    q: "How long does onboarding take?",
    a: "Getting started takes under two minutes. Simply register an analyst account, verify your email, and drag-and-drop any standard .pcap or .pcapng capture directly into the web application.",
  },
];

export function Faq() {
  return (
    <Section>
      <div className="grid gap-12 lg:grid-cols-[0.8fr_1.2fr] lg:items-start">
        <SectionHeading
          align="left"
          eyebrow="FAQ"
          title="Answers for security architects"
          description="Still have questions? Our solutions engineers respond within one business day."
        />
        <Reveal>
          <Accordion type="single" collapsible className="w-full">
            {faqs.map((f, i) => (
              <AccordionItem key={f.q} value={`item-${i}`} className="border-border">
                <AccordionTrigger className="text-left text-sm font-medium hover:text-primary">
                  {f.q}
                </AccordionTrigger>
                <AccordionContent className="text-sm leading-relaxed text-muted-foreground">
                  {f.a}
                </AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        </Reveal>
      </div>
    </Section>
  );
}
