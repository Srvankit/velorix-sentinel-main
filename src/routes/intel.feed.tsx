import { createFileRoute } from "@tanstack/react-router";
import { Rss } from "lucide-react";
import { useState } from "react";

import { DataTable, PageHeader, type DataTableColumn } from "@/components/common";
import { AwaitingFeed, IntelMetric, IntelSeverity } from "@/components/intel";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { IOC_SEVERITIES } from "@/data/ioc-catalog";
import type { ThreatFeedEvent } from "@/types/intel";
import { threatIntelService } from "@/services/threatIntelService";
import { useIntelResource } from "@/hooks/useIntelResource";

export const Route = createFileRoute("/intel/feed")({
  head: () => ({
    meta: [
      { title: "Intelligence Feed · Velorix Sentinel" },
      {
        name: "description",
        content: "Live threat intelligence feed sources and ingestion status.",
      },
      { property: "og:title", content: "Intelligence Feed · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Live threat intelligence feed sources and ingestion status.",
      },
    ],
  }),
  component: ThreatFeedPage,
});

function ThreatFeedPage() {
  const [severity, setSeverity] = useState("all");
  const state = useIntelResource(
    (signal) => threatIntelService.listFeed({ severity }, signal),
    [severity],
  );
  const rows: ThreatFeedEvent[] = state.status === "ready" ? state.data.content : [];

  const columns: Array<DataTableColumn<ThreatFeedEvent>> = [
    {
      key: "observedAt",
      header: "Timestamp",
      cell: (row) => new Date(row.observedAt).toLocaleString(),
      sortValue: (row) => row.observedAt,
      sortable: true,
    },
    { key: "name", header: "Threat", cell: (row) => row.name },
    {
      key: "severity",
      header: "Severity",
      cell: (row) => <IntelSeverity severity={row.severity} />,
      sortValue: (row) => row.severity,
      sortable: true,
    },
    { key: "status", header: "Status", cell: (row) => row.status },
    { key: "source", header: "Source", cell: (row) => row.source },
    { key: "country", header: "Country", cell: (row) => row.country ?? "—" },
    { key: "category", header: "Category", cell: (row) => row.category },
    {
      key: "riskScore",
      header: "Risk",
      cell: (row) => row.riskScore,
      sortValue: (row) => row.riskScore,
      sortable: true,
      align: "right",
    },
  ];

  return (
    <>
      <PageHeader
        title="Threat Feed"
        description="Streaming adversary events with severity, provenance, geography and risk scoring."
      />

      <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <IntelMetric label="Events (24h)" value={null} icon={Rss} />
        <IntelMetric label="Critical events" value={null} />
        <IntelMetric label="Open triage" value={null} />
        <IntelMetric label="Feed latency" value={null} hint="Awaiting Live Threat Intelligence" />
      </div>

      <AwaitingFeed
        className="mt-4"
        detail="No live telemetry connected. Events populate this feed the moment an intelligence source streams data."
      />

      <div className="glass-panel mt-6 p-5">
        <DataTable
          rows={rows}
          columns={columns}
          rowKey={(row) => row.id}
          isLoading={state.status === "loading"}
          searchAccessor={(row) => `${row.name} ${row.source} ${row.category} ${row.country ?? ""}`}
          searchPlaceholder="Search threat name, source or category…"
          emptyTitle="No threat events"
          emptyDescription="Awaiting Live Threat Intelligence — this feed intentionally shows no synthetic alerts."
          toolbar={
            <Select value={severity} onValueChange={setSeverity}>
              <SelectTrigger className="h-9 w-[160px]" aria-label="Filter by severity">
                <SelectValue placeholder="Severity" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All severities</SelectItem>
                {IOC_SEVERITIES.map((item) => (
                  <SelectItem key={item} value={item}>
                    {item}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          }
        />
      </div>
    </>
  );
}
