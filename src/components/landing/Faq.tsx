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
    a: "Velorix Sentinel inspects traffic out-of-band from a SPAN/TAP feed or from uploaded PCAP files, so there is no inline device and no added latency on production paths.",
  },
  {
    q: "Do you decrypt TLS traffic?",
    a: "Decryption is optional. Without keys we analyse TLS metadata, JA3/JA3S fingerprints, certificate chains, timing and volumetrics — enough to detect most C2 and exfiltration patterns. Key material can be supplied for full payload inspection.",
  },
  {
    q: "Where is my capture data stored?",
    a: "Captures are encrypted at rest with per-tenant keys. Enterprise customers can pin storage to a specific region or deploy fully on-premises.",
  },
  {
    q: "How accurate are the AI detections?",
    a: "Detections combine deterministic signatures with behavioural models, and every AI conclusion cites the packets that produced it.",
  },
  {
    q: "Which tools does Velorix Sentinel integrate with?",
    a: "Native integrations exist for Splunk, Microsoft Sentinel, Elastic, Chronicle, PagerDuty, Slack and generic webhooks, plus a full REST API.",
  },
  {
    q: "How long does deployment take?",
    a: "Most teams are analysing live traffic within an afternoon. Cloud onboarding requires no agents; sensor deployment is a single container per segment.",
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
