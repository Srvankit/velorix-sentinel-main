import { createFileRoute } from "@tanstack/react-router";
import { Bug } from "lucide-react";
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
import type { CveRecord } from "@/types/intel";
import { threatIntelService } from "@/services/threatIntelService";
import { useIntelResource } from "@/hooks/useIntelResource";

export const Route = createFileRoute("/intel/cve")({
  head: () => ({
    meta: [
      { title: "CVE Explorer · Velorix Sentinel" },
      {
        name: "description",
        content: "Vulnerability intelligence with CVSS scoring and exploit context.",
      },
      { property: "og:title", content: "CVE Explorer · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Vulnerability intelligence with CVSS scoring and exploit context.",
      },
    ],
  }),
  component: CvePage,
});

function CvePage() {
  const [severity, setSeverity] = useState("all");
  const state = useIntelResource(
    (signal) => threatIntelService.listCves({ severity }, signal),
    [severity],
  );
  const rows: CveRecord[] = state.status === "ready" ? state.data.content : [];

  const columns: Array<DataTableColumn<CveRecord>> = [
    {
      key: "id",
      header: "CVE",
      cell: (row) => <span className="font-mono text-xs">{row.id}</span>,
    },
    { key: "title", header: "Title", cell: (row) => row.title },
    {
      key: "severity",
      header: "Severity",
      cell: (row) => <IntelSeverity severity={row.severity} />,
      sortValue: (row) => row.severity,
      sortable: true,
    },
    {
      key: "cvss",
      header: "CVSS",
      cell: (row) => row.cvssScore.toFixed(1),
      sortValue: (row) => row.cvssScore,
      sortable: true,
    },
    {
      key: "published",
      header: "Published",
      cell: (row) => new Date(row.publishedAt).toLocaleDateString(),
      sortValue: (row) => row.publishedAt,
      sortable: true,
    },
    { key: "vendor", header: "Vendor", cell: (row) => row.vendor },
    {
      key: "products",
      header: "Affected products",
      cell: (row) => row.affectedProducts.join(", "),
    },
    {
      key: "exploit",
      header: "Exploit",
      cell: (row) => (row.exploitAvailable ? "Available" : "None known"),
      align: "right",
    },
  ];

  return (
    <>
      <PageHeader
        title="CVE Explorer"
        description="Vulnerability intelligence with CVSS scoring, exploit availability, mitigation and references."
      />

      <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <IntelMetric label="Tracked CVEs" value={null} icon={Bug} />
        <IntelMetric label="Critical (CVSS ≥ 9)" value={null} />
        <IntelMetric label="Known exploited" value={null} />
        <IntelMetric label="Last sync" value={null} hint="Awaiting Live Threat Feed" />
      </div>

      <AwaitingFeed
        className="mt-4"
        label="Awaiting Live Threat Feed"
        detail="CVE records stream from the NVD-backed intelligence service. Mitigation and reference links appear per record."
      />

      <div className="glass-panel mt-6 p-5">
        <DataTable
          rows={rows}
          columns={columns}
          rowKey={(row) => row.id}
          isLoading={state.status === "loading"}
          searchAccessor={(row) =>
            `${row.id} ${row.title} ${row.vendor} ${row.affectedProducts.join(" ")}`
          }
          searchPlaceholder="Search CVE ID, product or vendor…"
          emptyTitle="No CVE records"
          emptyDescription="Awaiting Live Threat Feed — vulnerability data will populate this table."
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
