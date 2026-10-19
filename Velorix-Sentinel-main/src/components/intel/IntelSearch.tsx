import { Link } from "@tanstack/react-router";
import { Search } from "lucide-react";
import { useMemo, useState } from "react";

import { AwaitingFeed } from "./AwaitingFeed";
import { Input } from "@/components/ui/input";
import { LIBRARY_ENTRIES } from "@/data/intel-library";
import { MALWARE_FAMILIES } from "@/data/malware";
import { MITRE_TACTICS } from "@/data/mitre";
import { IOC_CATEGORIES } from "@/data/ioc-catalog";
import type { IntelSearchResult } from "@/types/intel";

/**
 * Universal threat search across locally indexed reference material
 * (MITRE, malware, IOC classes, library). CVE and report results join the
 * index once the intelligence backend exposes `/api/v1/intel/search`.
 */
function buildIndex(): IntelSearchResult[] {
  const mitre: IntelSearchResult[] = MITRE_TACTICS.flatMap((tactic) =>
    tactic.techniques.map((technique) => ({
      id: `${tactic.id}-${technique.id}`,
      kind: "mitre" as const,
      title: `${technique.id} · ${technique.name}`,
      subtitle: `${tactic.name} — ${technique.description}`,
      href: "/intel/mitre",
    })),
  );

  const malware: IntelSearchResult[] = MALWARE_FAMILIES.map((family) => ({
    id: family.slug,
    kind: "malware",
    title: family.name,
    subtitle: family.description,
    href: "/intel/malware",
  }));

  const iocs: IntelSearchResult[] = IOC_CATEGORIES.map((category) => ({
    id: category.kind,
    kind: "ioc",
    title: `${category.plural} indicators`,
    subtitle: category.description,
    href: "/intel/iocs",
  }));

  const library: IntelSearchResult[] = LIBRARY_ENTRIES.map((entry) => ({
    id: entry.slug,
    kind: "report",
    title: entry.title,
    subtitle: entry.summary,
    href: "/intel/library",
  }));

  return [...mitre, ...malware, ...iocs, ...library];
}

const KIND_LABEL: Record<IntelSearchResult["kind"], string> = {
  mitre: "MITRE",
  cve: "CVE",
  ioc: "IOC",
  malware: "Malware",
  report: "Library",
};

export function IntelSearch() {
  const [query, setQuery] = useState("");
  const index = useMemo(buildIndex, []);

  const results = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return [];
    return index
      .filter((entry) => `${entry.title} ${entry.subtitle}`.toLowerCase().includes(needle))
      .slice(0, 12);
  }, [index, query]);

  return (
    <div className="space-y-3">
      <div className="relative">
        <Search
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search MITRE techniques, malware families, IOC classes, library…"
          aria-label="Universal threat search"
          className="pl-9"
        />
      </div>

      {query.trim() && results.length === 0 && (
        <p className="rounded-lg border border-dashed border-border px-3 py-4 text-center text-xs text-muted-foreground">
          No reference material matched “{query}”.
        </p>
      )}

      <ul className="space-y-2">
        {results.map((result) => (
          <li key={`${result.kind}-${result.id}`}>
            <Link
              to={result.href}
              className="focus-ring flex items-start gap-3 rounded-xl border border-border/70 bg-card/40 p-3 transition-colors hover:border-primary/40"
            >
              <span className="mt-0.5 shrink-0 rounded-md border border-primary/30 bg-primary/10 px-2 py-0.5 text-[11px] text-primary">
                {KIND_LABEL[result.kind]}
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium">{result.title}</span>
                <span className="line-clamp-2 text-xs text-muted-foreground">
                  {result.subtitle}
                </span>
              </span>
            </Link>
          </li>
        ))}
      </ul>

      <AwaitingFeed
        compact
        label="CVE and threat-report search awaits the live intelligence backend"
      />
    </div>
  );
}
