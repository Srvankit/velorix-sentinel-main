import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";

import { PageHeader } from "@/components/common";
import { IntelMetric, ThreatMap } from "@/components/intel";
import type { ThreatMapPoint } from "@/types/intel";
import { threatIntelService } from "@/services/threatIntelService";
import { useIntelResource } from "@/hooks/useIntelResource";

export const Route = createFileRoute("/intel/map")({
  head: () => ({
    meta: [
      { title: "Global Threat Map · Velorix Sentinel" },
      {
        name: "description",
        content: "Geographic view of observed threat activity and source regions.",
      },
      { property: "og:title", content: "Global Threat Map · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Geographic view of observed threat activity and source regions.",
      },
    ],
  }),
  component: ThreatMapPage,
});

function ThreatMapPage() {
  const [country, setCountry] = useState("");
  const state = useIntelResource((signal) => threatIntelService.getMap(signal));
  const points: ThreatMapPoint[] = state.status === "ready" ? state.data : [];

  return (
    <>
      <PageHeader
        title="Global Threat Map"
        description="Geographic distribution of attack origins and threat density across connected telemetry."
      />

      <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <IntelMetric label="Countries observed" value={points.length ? points.length : null} />
        <IntelMetric label="Top origin" value={null} />
        <IntelMetric label="Peak density" value={null} />
        <IntelMetric label="Telemetry status" value={null} hint="No live telemetry connected." />
      </div>

      <div className="glass-panel mt-6 p-5">
        <ThreatMap points={points} countryFilter={country} onCountryFilterChange={setCountry} />
      </div>
    </>
  );
}
