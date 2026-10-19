import { createFileRoute } from "@tanstack/react-router";
import {
  Activity,
  Bug,
  Crosshair,
  Flame,
  Globe2,
  Layers,
  Radar,
  Rss,
  ShieldAlert,
  Sparkles,
} from "lucide-react";

import { PageHeader } from "@/components/common";
import { AwaitingFeed, IntelCard, IntelMetric, IntelSearch } from "@/components/intel";
import { MALWARE_FAMILIES } from "@/data/malware";
import { MITRE_TACTICS, MITRE_TECHNIQUE_COUNT } from "@/data/mitre";
import { IOC_CATEGORIES } from "@/data/ioc-catalog";
import { threatIntelService } from "@/services/threatIntelService";
import { useIntelResource } from "@/hooks/useIntelResource";

export const Route = createFileRoute("/intel/")({
  head: () => ({
    meta: [
      { title: "Threat Intelligence · Velorix Sentinel" },
      {
        name: "description",
        content:
          "Threat intelligence centre for indicators, adversaries, malware and vulnerabilities.",
      },
      { property: "og:title", content: "Threat Intelligence · Velorix Sentinel" },
      {
        property: "og:description",
        content:
          "Threat intelligence centre for indicators, adversaries, malware and vulnerabilities.",
      },
    ],
  }),
  component: IntelOverviewPage,
});

const THREAT_CATEGORIES = [
  "Command & Control",
  "Reconnaissance",
  "Exploitation",
  "Credential Theft",
  "Denial of Service",
  "Data Exfiltration",
];

function IntelOverviewPage() {
  const landscape = useIntelResource((signal) => threatIntelService.getLandscape(signal));
  const sources = useIntelResource((signal) => threatIntelService.listSources(signal));

  const awaiting = landscape.status !== "ready";
  const plannedCoverage = MITRE_TACTICS.flatMap((tactic) => tactic.techniques).filter(
    (technique) => technique.coverage === "in-progress",
  ).length;

  return (
    <>
      <PageHeader
        title="Threat Intelligence Center"
        description="Consolidated adversary intelligence: landscape, indicators, ATT&CK coverage, vulnerabilities and reporting."
        actions={
          <span className="inline-flex items-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-xs text-primary">
            <Sparkles className="size-3.5" aria-hidden="true" />
            Awaiting Live Threat Intelligence
          </span>
        }
      />

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        <IntelCard
          title="Threat Landscape"
          description="Rolling 24-hour adversary activity across connected sources."
          icon={Activity}
          className="lg:col-span-2"
        >
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <IntelMetric label="Active campaigns" value={null} icon={Flame} />
            <IntelMetric label="Indicators ingested" value={null} icon={Crosshair} />
            <IntelMetric label="Critical advisories" value={null} icon={ShieldAlert} />
            <IntelMetric label="Sources online" value={null} icon={Rss} />
          </div>
          {awaiting && <AwaitingFeed className="mt-4" />}
        </IntelCard>

        <IntelCard
          title="IOC Summary"
          description="Indicator classes tracked by the platform."
          icon={Crosshair}
        >
          <ul className="space-y-2">
            {IOC_CATEGORIES.slice(0, 6).map((category) => (
              <li
                key={category.kind}
                className="flex items-center justify-between rounded-lg border border-border/60 px-3 py-2 text-xs"
              >
                <span className="flex items-center gap-2">
                  <category.icon className="size-3.5 text-primary/80" aria-hidden="true" />
                  {category.plural}
                </span>
                <span className="text-muted-foreground">Awaiting feed</span>
              </li>
            ))}
          </ul>
        </IntelCard>

        <IntelCard
          title="Emerging Threats"
          description="Newly observed campaigns and tooling."
          icon={Radar}
        >
          <AwaitingFeed detail="Emerging campaign tracking activates when an intelligence source is connected." />
        </IntelCard>

        <IntelCard
          title="Threat Categories"
          description="Classification taxonomy used across the platform."
          icon={Layers}
        >
          <div className="flex flex-wrap gap-2">
            {THREAT_CATEGORIES.map((category) => (
              <span
                key={category}
                className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground"
              >
                {category}
              </span>
            ))}
          </div>
          <AwaitingFeed compact className="mt-3" label="Per-category volumes await live data" />
        </IntelCard>

        <IntelCard
          title="MITRE Coverage"
          description="Enterprise matrix mapped to detection roadmap."
          icon={ShieldAlert}
        >
          <div className="grid gap-3 sm:grid-cols-3">
            <IntelMetric label="Tactics" value={MITRE_TACTICS.length} />
            <IntelMetric label="Techniques" value={MITRE_TECHNIQUE_COUNT} />
            <IntelMetric
              label="In progress"
              value={plannedCoverage}
              hint="Detections being built"
            />
          </div>
        </IntelCard>

        <IntelCard
          title="Top CVEs"
          description="Highest-risk vulnerabilities in the current window."
          icon={Bug}
        >
          <AwaitingFeed detail="CVE ranking populates from the NVD-backed intelligence service." />
        </IntelCard>

        <IntelCard title="Malware Families" description="Reference knowledge base." icon={Bug}>
          <div className="flex flex-wrap gap-2">
            {MALWARE_FAMILIES.map((family) => (
              <span
                key={family.slug}
                className="rounded-full border border-border px-3 py-1 text-xs text-muted-foreground"
              >
                {family.name}
              </span>
            ))}
          </div>
        </IntelCard>

        <IntelCard title="Threat Feed" description="Streaming adversary events." icon={Rss}>
          <AwaitingFeed detail="No live telemetry connected. Events appear here in real time once a feed is attached." />
        </IntelCard>

        <IntelCard
          title="Intelligence Sources"
          description="Commercial, open-source, government and internal providers."
          icon={Globe2}
        >
          {sources.status === "ready" && sources.data.length > 0 ? (
            <ul className="space-y-2 text-xs">
              {sources.data.map((source) => (
                <li key={source.id} className="flex items-center justify-between">
                  <span>{source.name}</span>
                  <span className="text-muted-foreground">{source.status}</span>
                </li>
              ))}
            </ul>
          ) : (
            <AwaitingFeed detail="No intelligence sources are configured for this workspace yet." />
          )}
        </IntelCard>

        <IntelCard
          title="Universal Threat Search"
          description="Search MITRE, malware, IOC classes and library material."
          icon={Radar}
          className="lg:col-span-3"
        >
          <IntelSearch />
        </IntelCard>
      </div>
    </>
  );
}
