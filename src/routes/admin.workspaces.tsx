import { createFileRoute } from "@tanstack/react-router";
import { Layers } from "lucide-react";

import { AdminSection, AwaitingAdminData } from "@/components/admin";
import { useAdminResource } from "@/hooks/useAdminResource";
import { adminService } from "@/services/adminService";

export const Route = createFileRoute("/admin/workspaces")({
  head: () => ({
    meta: [
      { title: "Workspace Management · Velorix Sentinel" },
      {
        name: "description",
        content: "Workspace isolation, storage quotas and per-workspace policy controls.",
      },
      { property: "og:title", content: "Workspace Management · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Workspace isolation, storage quotas and per-workspace policy controls.",
      },
    ],
  }),
  component: WorkspacesPage,
});

const WORKSPACE_ATTRIBUTES = [
  { label: "Members", detail: "Analysts and managers with access to the workspace." },
  { label: "Assigned Teams", detail: "Teams routed to this workspace for triage ownership." },
  { label: "Default Policies", detail: "Retention, MFA and network policy inherited by members." },
  { label: "Storage Usage", detail: "Capture storage consumed against the workspace quota." },
  { label: "Region", detail: "Data residency region for captures and derived artefacts." },
  { label: "Status", detail: "Active, suspended or archived lifecycle state." },
];

function WorkspacesPage() {
  const workspaces = useAdminResource(adminService.listWorkspaces);
  const rows = workspaces.data ?? [];

  return (
    <div className="space-y-4">
      <AdminSection
        title="Workspaces"
        description="Isolated analysis environments with their own membership, policies and storage quota."
      >
        {rows.length === 0 ? (
          <AwaitingAdminData
            icon={Layers}
            detail="Workspaces load from the administration backend. No environments are simulated in this console."
          />
        ) : (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {rows.map((workspace) => (
              <article
                key={workspace.id}
                className="rounded-2xl border border-border/70 bg-card/40 p-4 backdrop-blur-xl"
              >
                <h3 className="text-sm font-semibold">{workspace.name}</h3>
                <p className="mt-1 text-xs text-muted-foreground">
                  {workspace.organization ?? "—"} · {workspace.region ?? "—"}
                </p>
                <dl className="mt-3 grid grid-cols-2 gap-2 text-[11px]">
                  <div>
                    <dt className="text-muted-foreground">Members</dt>
                    <dd className="font-medium">{workspace.members ?? "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Teams</dt>
                    <dd className="font-medium">{workspace.teams ?? "—"}</dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        )}
      </AdminSection>

      <AdminSection
        title="Workspace Attributes"
        description="What the backend will supply for every workspace card."
      >
        <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
          {WORKSPACE_ATTRIBUTES.map((attribute) => (
            <div
              key={attribute.label}
              className="rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5"
            >
              <p className="text-xs font-medium">{attribute.label}</p>
              <p className="mt-0.5 text-[11px] text-muted-foreground">{attribute.detail}</p>
            </div>
          ))}
        </div>
      </AdminSection>
    </div>
  );
}
