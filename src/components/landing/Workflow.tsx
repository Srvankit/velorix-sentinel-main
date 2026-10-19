import { motion } from "framer-motion";
import { Brain, FileText, Radar, ScanSearch, ShieldCheck, Upload } from "lucide-react";

import { Reveal, Section, SectionHeading } from "./Section";

const steps = [
  { icon: Upload, title: "Upload PCAP", copy: "Drag in captures or stream from a sensor." },
  { icon: ScanSearch, title: "Analyze Packets", copy: "Full protocol decode at line rate." },
  { icon: Radar, title: "Detect Threats", copy: "Signature plus behavioural detection." },
  { icon: Brain, title: "AI Analysis", copy: "Correlation, scoring and narrative." },
  { icon: FileText, title: "Generate Report", copy: "Executive and technical output." },
  { icon: ShieldCheck, title: "Take Action", copy: "Contain, rotate, and push to SIEM." },
];

export function Workflow() {
  return (
    <Section id="solutions" className="relative">
      <SectionHeading
        eyebrow="Workflow"
        title="From raw capture to contained incident"
        description="A repeatable, auditable pipeline your analysts can trust end to end."
      />

      <div className="relative mt-16">
        <div
          aria-hidden
          className="absolute left-0 right-0 top-7 hidden h-px bg-gradient-to-r from-transparent via-primary/40 to-transparent lg:block"
        />
        <ol className="grid gap-8 sm:grid-cols-2 lg:grid-cols-6 lg:gap-4">
          {steps.map((s, i) => (
            <li key={s.title} className="relative">
              <Reveal delay={i * 0.08} className="flex flex-col items-center text-center">
                <motion.span
                  whileHover={{ scale: 1.08 }}
                  className="relative z-10 grid size-14 place-items-center rounded-2xl border border-primary/30 bg-card text-primary shadow-lg"
                >
                  <s.icon className="size-6" aria-hidden="true" />
                  <span className="absolute -right-2 -top-2 grid size-6 place-items-center rounded-full bg-gradient-brand font-mono text-[10px] font-semibold text-primary-foreground">
                    {i + 1}
                  </span>
                </motion.span>
                <h3 className="mt-4 text-sm font-semibold">{s.title}</h3>
                <p className="mt-1 text-xs text-muted-foreground">{s.copy}</p>
              </Reveal>
            </li>
          ))}
        </ol>
      </div>
    </Section>
  );
}
