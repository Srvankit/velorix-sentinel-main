import { createFileRoute } from "@tanstack/react-router";
import { FileSearch, ShieldCheck } from "lucide-react";
import { toast } from "sonner";

import { AwaitingBackend, EvidenceVault, IncidentCard } from "@/components/incidents";
import { PageHeader } from "@/components/common";

export const Route = createFileRoute("/incidents/evidence")({
  head: () => ({
    meta: [
      { title: "Evidence Vault · Velorix Sentinel" },
      {
        name: "description",
        content: "Chain-of-custody evidence store for incident investigations.",
      },
      { property: "og:title", content: "Evidence Vault · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Chain-of-custody evidence store for incident investigations.",
      },
    ],
  }),
  component: EvidencePage,
});

const CUSTODY_RULES = [
  "Every artefact is hashed (SHA-256) at collection time and the digest is recorded before storage.",
  "Each transfer records who handled the item, when, and the action taken — acquired, transferred, analysed, or released.",
  "Custody entries are append-only; corrections are added as new entries rather than edits.",
  "Evidence is scoped to its parent case and only visible to analysts with access to that case.",
];

function EvidencePage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Evidence Vault"
        description="Artefacts collected across all investigations, with full chain of custody."
      />

      <IncidentCard
        title="Collected Evidence"
        description="PCAP extracts, logs, files, screenshots and memory captures"
        icon={FileSearch}
      >
        <EvidenceVault
          items={[]}
          onUpload={() =>
            toast.info("Evidence upload", { description: "Available after backend integration." })
          }
        />
      </IncidentCard>

      <IncidentCard
        title="Chain of Custody Policy"
        description="How Velorix Sentinel handles evidence integrity"
        icon={ShieldCheck}
      >
        <ul className="space-y-2">
          {CUSTODY_RULES.map((rule) => (
            <li
              key={rule}
              className="rounded-xl border border-border/70 bg-card/40 p-3 text-sm text-muted-foreground"
            >
              {rule}
            </li>
          ))}
        </ul>
      </IncidentCard>

      <AwaitingBackend
        compact
        icon={FileSearch}
        title="Evidence storage is not connected yet."
        detail="Once the response backend ships, artefacts uploaded to a case appear here with their custody trail. No sample evidence is shown."
      />
    </div>
  );
}
