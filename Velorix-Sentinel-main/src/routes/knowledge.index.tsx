import { Link, createFileRoute } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { ArrowRight, BookOpen, Clock } from "lucide-react";
import { useMemo, useState } from "react";

import { PageHeader } from "@/components/common";
import { KNOWLEDGE_TOPICS } from "@/data/knowledge";

export const Route = createFileRoute("/knowledge/")({
  head: () => ({
    meta: [
      { title: "Security Knowledge Center · Velorix Sentinel" },
      {
        name: "description",
        content: "Reference articles on network protocols, attack techniques and detection logic.",
      },
      { property: "og:title", content: "Security Knowledge Center · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Reference articles on network protocols, attack techniques and detection logic.",
      },
    ],
  }),
  component: KnowledgeIndexPage,
});

const CATEGORIES = ["All", "Frameworks", "Vulnerabilities", "Operations", "Fundamentals"] as const;

function KnowledgeIndexPage() {
  const [category, setCategory] = useState<(typeof CATEGORIES)[number]>("All");

  const topics = useMemo(
    () =>
      category === "All"
        ? KNOWLEDGE_TOPICS
        : KNOWLEDGE_TOPICS.filter((topic) => topic.category === category),
    [category],
  );

  return (
    <>
      <PageHeader
        title="Security Knowledge Center"
        description="Curated reference material for analysts — frameworks, vulnerabilities, hunting and protocol fundamentals."
        actions={
          <span className="inline-flex items-center gap-2 rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">
            <BookOpen className="size-3.5" aria-hidden="true" />
            {KNOWLEDGE_TOPICS.length} articles
          </span>
        }
      />

      <div className="mt-6 flex flex-wrap gap-2">
        {CATEGORIES.map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => setCategory(item)}
            aria-pressed={category === item}
            className={
              category === item
                ? "focus-ring rounded-full border border-primary/40 bg-primary/15 px-3 py-1 text-xs font-medium text-primary"
                : "focus-ring rounded-full border border-border px-3 py-1 text-xs text-muted-foreground transition-colors hover:text-foreground"
            }
          >
            {item}
          </button>
        ))}
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {topics.map((topic, index) => (
          <motion.div
            key={topic.slug}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: index * 0.04, ease: [0.22, 1, 0.36, 1] }}
          >
            <Link
              to="/knowledge/$topic"
              params={{ topic: topic.slug }}
              className="focus-ring group glass-panel flex h-full flex-col rounded-2xl p-5 transition-all hover:-translate-y-0.5 hover:border-primary/40"
            >
              <span className="grid size-10 place-items-center rounded-xl bg-primary/10 text-primary">
                <topic.icon className="size-5" aria-hidden="true" />
              </span>
              <h2 className="mt-4 font-display text-base font-semibold tracking-tight">
                {topic.title}
              </h2>
              <p className="mt-1 flex-1 text-sm text-muted-foreground">{topic.tagline}</p>
              <span className="mt-4 grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2 text-xs text-muted-foreground">
                <span className="flex min-w-0 items-center gap-1.5">
                  <Clock className="size-3 shrink-0" aria-hidden="true" />
                  <span className="truncate">
                    {topic.readingMinutes} min · {topic.category}
                  </span>
                </span>
                <ArrowRight
                  className="size-3.5 shrink-0 transition-transform group-hover:translate-x-1"
                  aria-hidden="true"
                />
              </span>
            </Link>
          </motion.div>
        ))}
      </div>
    </>
  );
}
