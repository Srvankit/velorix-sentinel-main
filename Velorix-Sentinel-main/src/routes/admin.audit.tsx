import { createFileRoute } from "@tanstack/react-router";
import { Download } from "lucide-react";

import { AdminSection } from "@/components/admin";
import { DataTable, type DataTableColumn } from "@/components/common";
import { Button } from "@/components/ui/button";
import { useAdminResource } from "@/hooks/useAdminResource";
import { ADMIN_WRITE_READY, adminService } from "@/services/adminService";
import type { AdminAuditEntry } from "@/types/admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/audit")({
  head: () => ({
    meta: [
      { title: "Audit Logs · Velorix Sentinel" },
      {
        name: "description",
        content:
          "Immutable administrative audit records covering privileged actions across the platform.",
      },
      { property: "og:title", content: "Audit Logs · Velorix Sentinel" },
      {
        property: "og:description",
        content:
          "Immutable administrative audit records covering privileged actions across the platform.",
      },
    ],
  }),
  component: AuditLogsPage,
});

const OUTCOME_TONE: Record<AdminAuditEntry["outcome"], string> = {
  SUCCESS: "text-emerald-400",
  FAILURE: "text-primary",
  DENIED: "text-amber-400",
  PENDING: "text-muted-foreground",
};

const COLUMNS: DataTableColumn<AdminAuditEntry>[] = [
  {
    key: "timestamp",
    header: "Timestamp",
    sortable: true,
    sortValue: (row) => row.timestamp,
    cell: (row) => <span className="font-mono text-[11px]">{row.timestamp}</span>,
  },
  {
    key: "actor",
    header: "Administrator",
    cell: (row) => (
      <div className="min-w-0">
        <p className="truncate text-xs font-medium">{row.actor}</p>
        <p className="truncate text-[11px] text-muted-foreground">{row.actorEmail ?? "—"}</p>
      </div>
    ),
  },
  { key: "action", header: "Action", cell: (row) => row.action },
  { key: "target", header: "Target", cell: (row) => row.target ?? "—" },
  {
    key: "outcome",
    header: "Status",
    cell: (row) => (
      <span className={cn("text-[11px] font-medium", OUTCOME_TONE[row.outcome])}>
        {row.outcome}
      </span>
    ),
  },
  {
    key: "ip",
    header: "IP Address",
    cell: (row) => <span className="font-mono text-[11px]">{row.ipAddress ?? "—"}</span>,
  },
  { key: "device", header: "Device", cell: (row) => row.device ?? "—" },
];

function AuditLogsPage() {
  const logs = useAdminResource(() => adminService.listAuditLogs());

  return (
    <AdminSection
      title="Audit Logs"
      description="Immutable record of privileged administrative actions across the platform."
      actions={
        <Button size="sm" variant="outline" disabled={!ADMIN_WRITE_READY}>
          <Download className="size-4" aria-hidden="true" />
          Export
        </Button>
      }
    >
      <DataTable
        rows={logs.data ?? []}
        columns={COLUMNS}
        rowKey={(row) => row.id}
        isLoading={logs.status === "loading"}
        pageSize={25}
        searchAccessor={(row) =>
          `${row.actor} ${row.action} ${row.target ?? ""} ${row.ipAddress ?? ""}`
        }
        searchPlaceholder="Search by administrator, action, target or IP…"
        emptyTitle="Awaiting Live Data"
        emptyDescription="Audit records are produced by the backend audit pipeline. No entries are fabricated in this console."
      />
    </AdminSection>
  );
}
