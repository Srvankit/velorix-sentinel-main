import { ArrowRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Reveal, Section } from "./Section";

export function CtaBanner() {
  return (
    <Section>
      <Reveal className="ambient-grid glow-ring relative overflow-hidden rounded-3xl border border-primary/30 px-8 py-16 text-center sm:px-16">
        <h2 className="mx-auto max-w-2xl text-3xl font-semibold text-balance sm:text-4xl">
          See what your network is really saying
        </h2>
        <p className="mx-auto mt-4 max-w-xl text-muted-foreground text-pretty">
          Upload a capture and get a full AI threat report in under two minutes. No agents, no
          credit card.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Button size="lg" className="rounded-full">
            Start Free Analysis <ArrowRight className="ml-1 size-4" />
          </Button>
          <Button size="lg" variant="outline" className="rounded-full">
            Talk to Security Engineering
          </Button>
        </div>
      </Reveal>
    </Section>
  );
}
