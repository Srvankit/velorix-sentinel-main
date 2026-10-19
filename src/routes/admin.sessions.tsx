import { createFileRoute } from "@tanstack/react-router";

import { AdminSection } from "@/components/admin";
import { DataTable, type DataTableColumn } from "@/components/common";
import { Button } from "@/components/ui/button";
import { useAdminResource } from "@/hooks/useAdminResource";
import { ADMIN_WRITE_READY, adminService } from "@/services/adminService";
import type { UserSession } from "@/types/admin";

export const Route = createFileRoute("/admin/sessions")({
  head: () => ({
    meta: [
      { title: "Active Sessions · Velorix Sentinel" },
      {
        name: "description",
        content: "Review and revoke authenticated sessions across the organisation.",
      },
      { property: "og:title", content: "Active Sessions · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Review and revoke authenticated sessions across the organisation.",
      },
    ],
  }),
  component: SessionsPage,
});

const COLUMNS: DataTableColumn<UserSession>[] = [
  {
    key: "user",
    header: "User",
    sortable: true,
    sortValue: (row) => row.user,
    cell: (row) => <span className="text-xs font-medium">{row.user}</span>,
  },
  { key: "browser", header: "Browser", cell: (row) => row.browser ?? "—" },
  {
    key: "device",
    header: "Device",
    cell: (row) => [row.device, row.os].filter(Boolean).join(" · ") || "—",
  },
  { key: "location", header: "Location", cell: (row) => row.location ?? "—" },
  {
    key: "ip",
    header: "IP",
    cell: (row) => <span className="font-mono text-[11px]">{row.ipAddress ?? "—"}</span>,
  },
  {
    key: "loginAt",
    header: "Login Time",
    sortable: true,
    sortValue: (row) => row.loginAt ?? "",
    cell: (row) => row.loginAt ?? "—",
  },
  { key: "status", header: "Status", cell: (row) => row.status },
  {
    key: "actions",
    header: "",
    align: "right",
    cell: () => (
      <Button size="sm" variant="outline" disabled={!ADMIN_WRITE_READY}>
        Revoke
      </Button>
    ),
  },
];

function SessionsPage() {
  const sessions = useAdminResource(adminService.listSessions);

  return (
    <AdminSection
      title="Active Sessions"
      description="Every authenticated session, with originating device, network and lifecycle state."
    >
      <DataTable
        rows={sessions.data ?? []}
        columns={COLUMNS}
        rowKey={(row) => row.id}
        isLoading={sessions.status === "loading"}
        searchAccessor={(row) => `${row.user} ${row.ipAddress ?? ""} ${row.location ?? ""}`}
        searchPlaceholder="Search by user, IP or location…"
        emptyTitle="Awaiting Live Data"
        emptyDescription="Session inventory loads from the administration backend. No sessions are simulated here."
      />
    </AdminSection>
  );
}
