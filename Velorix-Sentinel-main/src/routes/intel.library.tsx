import { createFileRoute } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { BookOpen, Clock } from "lucide-react";
import { useMemo, useState } from "react";

import { PageHeader } from "@/components/common";
import { LIBRARY_ENTRIES } from "@/data/intel-library";

export const Route = createFileRoute("/intel/library")({
  head: () => ({
    meta: [
      { title: "Intelligence Library · Velorix Sentinel" },
      {
        name: "description",
        content: "Reference library of adversary doctrine, tradecraft and analytic techniques.",
      },
      { property: "og:title", content: "Intelligence Library · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Reference library of adversary doctrine, tradecraft and analytic techniques.",
      },
    ],
  }),
  component: LibraryPage,
});

const CATEGORIES = ["All", "Adversaries", "Frameworks", "Operations"] as const;

function LibraryPage() {
  const [category, setCategory] = useState<(typeof CATEGORIES)[number]>("All");

  const entries = useMemo(
    () =>
      category === "All"
        ? LIBRARY_ENTRIES
        : LIBRARY_ENTRIES.filter((entry) => entry.category === category),
    [category],
  );

  return (
    <>
      <PageHeader
        title="Knowledge Library"
        description="Threat intelligence doctrine: adversaries, frameworks and operational practice."
        actions={
          <span className="inline-flex items-center gap-2 rounded-full border border-border px-3 py-1 text-xs text-muted-foreground">
            <BookOpen className="size-3.5" aria-hidden="true" />
            {LIBRARY_ENTRIES.length} entries
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

      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {entries.map((entry, index) => (
          <motion.article
            key={entry.slug}
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35, delay: Math.min(index * 0.04, 0.3) }}
            className="glass-panel flex flex-col gap-3 p-5 transition-colors hover:border-primary/30"
          >
            <header className="flex items-center justify-between gap-2">
              <h2 className="text-sm font-semibold">{entry.title}</h2>
              <span className="rounded-full border border-border px-2 py-0.5 text-[11px] text-muted-foreground">
                {entry.category}
              </span>
            </header>
            <p className="text-xs leading-relaxed text-muted-foreground">{entry.summary}</p>
            <ul className="space-y-1">
              {entry.points.map((point) => (
                <li key={point} className="flex gap-2 text-xs text-muted-foreground">
                  <span
                    className="mt-1.5 size-1 shrink-0 rounded-full bg-primary/70"
                    aria-hidden="true"
                  />
                  {point}
                </li>
              ))}
            </ul>
            <footer className="mt-auto flex items-center gap-1.5 pt-1 text-[11px] text-muted-foreground">
              <Clock className="size-3" aria-hidden="true" /> {entry.readingMinutes} min read
            </footer>
          </motion.article>
        ))}
      </div>
    </>
  );
}
