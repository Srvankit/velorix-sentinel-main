import { DataTable, type DataTableColumn } from "@/components/common/DataTable";
import { SeverityBadge } from "@/components/common/SeverityBadge";
import { StatusBadge } from "@/components/common/StatusBadge";
import { useRecentThreats } from "@/hooks/useDashboard";
import type { ThreatRecord } from "@/types/dashboard";
import { formatRelativeTime } from "@/utils/format";

const columns: Array<DataTableColumn<ThreatRecord>> = [
  {
    key: "title",
    header: "Finding",
    sortable: true,
    sortValue: (row) => row.title,
    cell: (row) => (
      <span className="block max-w-72">
        <span className="block truncate font-medium">{row.title}</span>
        <span className="block truncate font-mono text-xs text-muted-foreground">
          {row.detectionRule}
        </span>
      </span>
    ),
  },
  {
    key: "severity",
    header: "Severity",
    sortable: true,
    sortValue: (row) => row.severity,
    cell: (row) => <SeverityBadge severity={row.severity} />,
  },
  {
    key: "hosts",
    header: "Source → Destination",
    cell: (row) => (
      <span className="font-mono text-xs text-muted-foreground">
        {row.sourceIp ?? "—"} → {row.destinationIp ?? "—"}
      </span>
    ),
  },
  {
    key: "status",
    header: "Status",
    sortable: true,
    sortValue: (row) => row.status,
    cell: (row) => <StatusBadge status={row.status} />,
  },
  {
    key: "detected",
    header: "Detected",
    align: "right",
    sortable: true,
    sortValue: (row) => new Date(row.detectedAt).getTime(),
    cell: (row) => (
      <span className="text-muted-foreground">{formatRelativeTime(row.detectedAt)}</span>
    ),
  },
];

/** Newest detection findings from `/dashboard/recent-threats`. */
export function RecentThreatsTable({ limit = 10 }: { limit?: number }) {
  const { data, isLoading, isError, error } = useRecentThreats(limit);

  return (
    <section className="glass-panel rounded-2xl p-5">
      <header className="mb-4">
        <h2 className="text-sm font-semibold tracking-tight">Recent threats</h2>
        <p className="text-xs text-muted-foreground">Findings raised by the detection engine</p>
      </header>

      {isError ? (
        <p className="text-sm text-muted-foreground">{error?.message}</p>
      ) : (
        <DataTable
          rows={data ?? []}
          columns={columns}
          rowKey={(row) => row.id}
          isLoading={isLoading}
          searchAccessor={(row) =>
            `${row.title} ${row.detectionRule} ${row.severity} ${row.sourceIp ?? ""} ${row.destinationIp ?? ""}`
          }
          searchPlaceholder="Search findings, rules or IPs…"
          pageSize={5}
          emptyTitle="No threats detected"
          emptyDescription="Run detection on a completed inspection to populate this feed."
        />
      )}
    </section>
  );
}
