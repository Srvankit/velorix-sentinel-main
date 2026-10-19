import { createFileRoute } from "@tanstack/react-router";
import { BookOpen, Clock } from "lucide-react";

import { GovernanceSection } from "@/components/governance/GovernanceSection";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { GOVERNANCE_ARTICLES } from "@/data/governance-knowledge";

export const Route = createFileRoute("/reports/knowledge")({
  head: () => ({
    meta: [
      { title: "Governance Knowledge · Velorix Sentinel" },
      {
        name: "description",
        content: "Reference guidance on governance, risk and compliance practice.",
      },
      { property: "og:title", content: "Governance Knowledge · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Reference guidance on governance, risk and compliance practice.",
      },
    ],
  }),
  component: ComplianceKnowledgeHub,
});

function ComplianceKnowledgeHub() {
  return (
    <GovernanceSection
      title="Compliance Knowledge Hub"
      description="Reference material on the frameworks, doctrines and programmes this platform reports against"
      actions={
        <span className="inline-flex items-center gap-1.5 text-[11px] text-muted-foreground">
          <BookOpen className="size-3.5" aria-hidden="true" />
          {GOVERNANCE_ARTICLES.length} articles
        </span>
      }
    >
      <Accordion type="single" collapsible className="w-full">
        {GOVERNANCE_ARTICLES.map((article) => (
          <AccordionItem key={article.slug} value={article.slug}>
            <AccordionTrigger className="text-left">
              <div className="min-w-0 pr-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium">{article.title}</span>
                  <span className="rounded-full border border-border bg-muted/30 px-2 py-0.5 text-[10px] tracking-wide text-muted-foreground uppercase">
                    {article.category}
                  </span>
                </div>
                <p className="mt-1 text-xs font-normal text-muted-foreground">{article.summary}</p>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <p className="mb-3 inline-flex items-center gap-1.5 text-[11px] text-muted-foreground">
                <Clock className="size-3" aria-hidden="true" />
                {article.readMinutes} min read
              </p>
              <div className="space-y-4">
                {article.sections.map((section) => (
                  <section key={section.heading}>
                    <h3 className="text-xs font-semibold tracking-wide text-primary uppercase">
                      {section.heading}
                    </h3>
                    <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                      {section.body}
                    </p>
                    {section.points && (
                      <ul className="mt-2 space-y-1">
                        {section.points.map((point) => (
                          <li
                            key={point}
                            className="flex items-start gap-2 text-xs text-muted-foreground"
                          >
                            <span
                              className="mt-1.5 size-1 shrink-0 rounded-full bg-primary"
                              aria-hidden="true"
                            />
                            <span>{point}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </section>
                ))}
              </div>
            </AccordionContent>
          </AccordionItem>
        ))}
      </Accordion>
    </GovernanceSection>
  );
}
