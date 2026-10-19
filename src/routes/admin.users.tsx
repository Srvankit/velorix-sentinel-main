import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { ShieldCheck, UserPlus } from "lucide-react";

import { AdminSection, AwaitingAdminData } from "@/components/admin";
import { DataTable, type DataTableColumn } from "@/components/common";
import { Button } from "@/components/ui/button";
import { useAdminResource } from "@/hooks/useAdminResource";
import { ADMIN_WRITE_READY, adminService } from "@/services/adminService";
import type { AdminUser, MfaStatus } from "@/types/admin";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin/users")({
  head: () => ({
    meta: [
      { title: "User Directory · Velorix Sentinel" },
      {
        name: "description",
        content:
          "Enterprise user directory with role assignment, MFA status and lifecycle actions.",
      },
      { property: "og:title", content: "User Directory · Velorix Sentinel" },
      {
        property: "og:description",
        content:
          "Enterprise user directory with role assignment, MFA status and lifecycle actions.",
      },
    ],
  }),
  component: UsersPage,
});

const MFA_TONE: Record<MfaStatus, string> = {
  ENFORCED: "text-emerald-400",
  ENABLED: "text-sky-400",
  NOT_ENROLLED: "text-primary",
  EXEMPT: "text-amber-400",
  UNKNOWN: "text-muted-foreground",
};

function UsersPage() {
  const users = useAdminResource(() => adminService.listUsers({ page: 0, size: 25 }));
  const [selected, setSelected] = useState<string[]>([]);
  const rows = users.data?.items ?? [];

  function toggle(id: string) {
    setSelected((previous) =>
      previous.includes(id) ? previous.filter((item) => item !== id) : [...previous, id],
    );
  }

  const columns: DataTableColumn<AdminUser>[] = [
    {
      key: "select",
      header: "",
      className: "w-10",
      cell: (row) => (
        <input
          type="checkbox"
          checked={selected.includes(row.id)}
          onChange={() => toggle(row.id)}
          aria-label={`Select ${row.email}`}
          className="focus-ring size-4 rounded border-input accent-primary"
        />
      ),
    },
    {
      key: "user",
      header: "User",
      sortable: true,
      sortValue: (row) => `${row.lastName} ${row.firstName}`,
      cell: (row) => (
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="grid size-8 shrink-0 place-items-center rounded-full bg-primary/10 text-[11px] font-semibold text-primary">
            {row.firstName.charAt(0)}
            {row.lastName.charAt(0)}
          </span>
          <div className="min-w-0">
            <p className="truncate text-xs font-medium">
              {row.firstName} {row.lastName}
            </p>
            <p className="truncate text-[11px] text-muted-foreground">{row.email}</p>
          </div>
        </div>
      ),
    },
    {
      key: "role",
      header: "Role",
      sortable: true,
      sortValue: (row) => row.role,
      cell: (row) => row.role,
    },
    { key: "department", header: "Department", cell: (row) => row.department ?? "—" },
    { key: "organization", header: "Organization", cell: (row) => row.organization ?? "—" },
    { key: "status", header: "Status", cell: (row) => row.status },
    {
      key: "lastLogin",
      header: "Last Login",
      sortable: true,
      sortValue: (row) => row.lastLoginAt ?? "",
      cell: (row) => row.lastLoginAt ?? "—",
    },
    {
      key: "mfa",
      header: "MFA",
      cell: (row) => (
        <span className={cn("text-[11px] font-medium", MFA_TONE[row.mfaStatus])}>
          {row.mfaStatus.replace("_", " ")}
        </span>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <AdminSection
        title="User Management"
        description="Directory of every identity across organizations, with role, department, MFA state and last sign-in."
        actions={
          <Button size="sm" variant="outline" disabled={!ADMIN_WRITE_READY}>
            <UserPlus className="size-4" aria-hidden="true" />
            Invite user
          </Button>
        }
      >
        <DataTable
          rows={rows}
          columns={columns}
          rowKey={(row) => row.id}
          isLoading={users.status === "loading"}
          pageSize={25}
          searchAccessor={(row) =>
            `${row.firstName} ${row.lastName} ${row.email} ${row.role} ${row.department ?? ""}`
          }
          searchPlaceholder="Search by name, email, role or department…"
          toolbar={
            <span className="text-[11px] text-muted-foreground">
              {selected.length > 0
                ? `${selected.length} selected — bulk actions available once write APIs ship`
                : "Bulk selection ready"}
            </span>
          }
          emptyTitle="Awaiting Live Data"
          emptyDescription="The user directory loads from the administration backend. No accounts are simulated here."
        />
      </AdminSection>

      <AdminSection
        title="Identity Hygiene"
        description="MFA coverage, dormant accounts and privileged account review."
      >
        <AwaitingAdminData
          icon={ShieldCheck}
          detail="Coverage percentages and dormant-account findings are computed server-side from the live directory."
        />
      </AdminSection>
    </div>
  );
}
