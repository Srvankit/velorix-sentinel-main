import { Link } from "@tanstack/react-router";

import { DataTable, type DataTableColumn } from "@/components/common/DataTable";
import { RiskBadge } from "@/components/common/RiskBadge";
import { StatusBadge } from "@/components/common/StatusBadge";
import { useRecentAnalyses } from "@/hooks/useDashboard";
import type { RecentAnalysis } from "@/types/dashboard";
import { formatNumber, formatRelativeTime } from "@/utils/format";

const columns: Array<DataTableColumn<RecentAnalysis>> = [
  {
    key: "file",
    header: "Capture",
    sortable: true,
    sortValue: (row) => row.originalFileName,
    cell: (row) => (
      <span className="block max-w-64 truncate font-medium">{row.originalFileName}</span>
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
    key: "packets",
    header: "Packets",
    align: "right",
    sortable: true,
    sortValue: (row) => row.totalPackets,
    cell: (row) => <span className="font-mono tabular-nums">{formatNumber(row.totalPackets)}</span>,
  },
  {
    key: "risk",
    header: "Risk",
    align: "right",
    sortable: true,
    sortValue: (row) => row.riskScore,
    cell: (row) => <RiskBadge score={row.riskScore} showLabel={false} />,
  },
  {
    key: "created",
    header: "Created",
    align: "right",
    sortable: true,
    sortValue: (row) => new Date(row.createdAt).getTime(),
    cell: (row) => (
      <span className="text-muted-foreground">{formatRelativeTime(row.createdAt)}</span>
    ),
  },
];

/** Recent inspection runs, newest first, straight from `/dashboard/recent-analysis`. */
export function RecentAnalysesTable({ limit = 10 }: { limit?: number }) {
  const { data, isLoading, isError, error } = useRecentAnalyses(limit);

  return (
    <section className="glass-panel rounded-2xl p-5">
      <header className="mb-4 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold tracking-tight">Recent analyses</h2>
          <p className="text-xs text-muted-foreground">
            Latest inspection runs across your captures
          </p>
        </div>
        <Link
          to="/upload"
          className="focus-ring rounded text-xs font-medium text-primary hover:underline"
        >
          Upload capture
        </Link>
      </header>

      {isError ? (
        <p className="text-sm text-muted-foreground">{error?.message}</p>
      ) : (
        <DataTable
          rows={data ?? []}
          columns={columns}
          rowKey={(row) => row.id}
          isLoading={isLoading}
          searchAccessor={(row) => `${row.originalFileName} ${row.status}`}
          searchPlaceholder="Search captures…"
          pageSize={5}
          emptyTitle="No analyses yet"
          emptyDescription="Upload a .pcap or .pcapng capture to run your first inspection."
        />
      )}
    </section>
  );
}
