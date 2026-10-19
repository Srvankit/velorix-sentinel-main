import { createFileRoute } from "@tanstack/react-router";
import { Building2, Globe2, Layers, Palette, Users } from "lucide-react";

import { AdminSection, AwaitingAdminData } from "@/components/admin";
import { DataTable, type DataTableColumn } from "@/components/common";
import { useAdminResource } from "@/hooks/useAdminResource";
import { adminService } from "@/services/adminService";
import type { Organization } from "@/types/admin";

export const Route = createFileRoute("/admin/organizations")({
  head: () => ({
    meta: [
      { title: "Organization Management · Velorix Sentinel" },
      {
        name: "description",
        content: "Manage organisation profile, business units and tenant branding.",
      },
      { property: "og:title", content: "Organization Management · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Manage organisation profile, business units and tenant branding.",
      },
    ],
  }),
  component: OrganizationsPage,
});

const COLUMNS: DataTableColumn<Organization>[] = [
  {
    key: "name",
    header: "Organization",
    sortable: true,
    sortValue: (row) => row.name,
    cell: (row) => (
      <div className="min-w-0">
        <p className="truncate text-xs font-medium">{row.name}</p>
        <p className="truncate text-[11px] text-muted-foreground">{row.primaryDomain ?? "—"}</p>
      </div>
    ),
  },
  { key: "region", header: "Region", cell: (row) => row.region ?? "—" },
  { key: "units", header: "Business Units", cell: (row) => row.businessUnits ?? "—" },
  { key: "departments", header: "Departments", cell: (row) => row.departments ?? "—" },
  { key: "workspaces", header: "Workspaces", cell: (row) => row.workspaces ?? "—" },
  { key: "status", header: "Status", cell: (row) => row.status },
];

const PROFILE_FIELDS = [
  {
    icon: Building2,
    label: "Organization Profile",
    detail: "Legal name, domain, industry, tax identity and primary contact.",
  },
  {
    icon: Layers,
    label: "Business Units & Departments",
    detail: "Hierarchy used for ownership routing and reporting rollups.",
  },
  {
    icon: Globe2,
    label: "Regions",
    detail: "Data residency region and operational footprint per tenant.",
  },
  {
    icon: Palette,
    label: "Branding",
    detail: "Logo, accent colour and login presentation for the tenant.",
  },
  {
    icon: Users,
    label: "Workspace Assignment",
    detail: "Which analysis workspaces the organization may access.",
  },
];

function OrganizationsPage() {
  const organizations = useAdminResource(adminService.listOrganizations);

  return (
    <div className="space-y-4">
      <AdminSection
        title="Organizations"
        description="Every tenant provisioned on this deployment, with hierarchy, region and status."
      >
        <DataTable
          rows={organizations.data ?? []}
          columns={COLUMNS}
          rowKey={(row) => row.id}
          isLoading={organizations.status === "loading"}
          searchAccessor={(row) => `${row.name} ${row.primaryDomain ?? ""} ${row.region ?? ""}`}
          searchPlaceholder="Search organizations, domains or regions…"
          emptyTitle="Awaiting Live Data"
          emptyDescription="Organizations load from the administration backend. No tenant records are simulated."
        />
      </AdminSection>

      <div className="grid gap-4 lg:grid-cols-2">
        <AdminSection
          title="Organization Settings"
          description="Configuration surfaces available once tenant management is connected."
        >
          <ul className="space-y-2">
            {PROFILE_FIELDS.map((field) => (
              <li
                key={field.label}
                className="flex items-start gap-3 rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5"
              >
                <field.icon className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
                <div className="min-w-0">
                  <p className="text-xs font-medium">{field.label}</p>
                  <p className="mt-0.5 text-[11px] text-muted-foreground">{field.detail}</p>
                </div>
              </li>
            ))}
          </ul>
        </AdminSection>

        <AdminSection
          title="Business Units"
          description="Departmental structure, owners and headcount per organization."
        >
          <AwaitingAdminData detail="Business unit hierarchy is resolved per organization from the administration backend." />
        </AdminSection>
      </div>
    </div>
  );
}
