import { createFileRoute } from "@tanstack/react-router";

import { PageHeader } from "@/components/common";
import { AwaitingFeed, MitreMatrix } from "@/components/intel";
import { MITRE_TACTICS, MITRE_TECHNIQUE_COUNT } from "@/data/mitre";

export const Route = createFileRoute("/intel/mitre")({
  head: () => ({
    meta: [
      { title: "MITRE ATT&CK Explorer · Velorix Sentinel" },
      {
        name: "description",
        content: "Navigate the ATT&CK matrix of adversary tactics and techniques.",
      },
      { property: "og:title", content: "MITRE ATT&CK Explorer · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Navigate the ATT&CK matrix of adversary tactics and techniques.",
      },
    ],
  }),
  component: MitrePage,
});

function MitrePage() {
  return (
    <>
      <PageHeader
        title="MITRE ATT&CK Explorer"
        description={`${MITRE_TACTICS.length} enterprise tactics · ${MITRE_TECHNIQUE_COUNT} techniques with detection and mitigation guidance.`}
      />

      <AwaitingFeed
        className="mt-6"
        label="Detection coverage is roadmap data"
        detail="Per-technique coverage percentages become live once the correlation engine reports mappings from real detections."
      />

      <div className="mt-6">
        <MitreMatrix />
      </div>
    </>
  );
}
