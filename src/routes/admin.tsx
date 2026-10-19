import { createFileRoute, Outlet } from "@tanstack/react-router";
import { Info } from "lucide-react";

import { ProtectedRoute } from "@/components/auth";
import { AdminTabs } from "@/components/admin";
import { PageHeader } from "@/components/common";
import { DashboardLayout } from "@/layouts";
import { ADMIN_BACKEND_READY } from "@/services/adminService";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/admin")({
  component: AdminLayout,
  head: () => ({
    meta: [
      { title: `Administration Center · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Enterprise administration for organizations, users, RBAC, workspaces, security policies, integrations, audit logs and system configuration.",
      },
      { property: "og:title", content: `Administration Center · ${APP_NAME}` },
      {
        property: "og:description",
        content:
          "Manage tenants, identities, roles, policies, integrations and platform configuration from one enterprise console.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function AdminLayout() {
  return (
    <ProtectedRoute roles={["ADMIN"]}>
      <DashboardLayout>
        <div className="space-y-6">
          <PageHeader
            title="Enterprise Administration Center"
            description="Organizations, identities, access control, policies, integrations and platform configuration."
          />

          {!ADMIN_BACKEND_READY && (
            <div
              role="status"
              className="flex items-start gap-3 rounded-xl border border-primary/25 bg-primary/5 px-4 py-3"
            >
              <Info className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
              <p className="text-xs text-muted-foreground">
                <span className="font-medium text-foreground">
                  Administration services are not connected yet.
                </span>{" "}
                Tenants, users, sessions, audit records and usage figures come exclusively from the
                administration backend. Sections show{" "}
                <span className="font-medium text-foreground">“Awaiting Live Data”</span> rather
                than sample records. Role, policy and integration catalogues below are shipped
                platform reference material.
              </p>
            </div>
          )}

          <AdminTabs />

          <Outlet />
        </div>
      </DashboardLayout>
    </ProtectedRoute>
  );
}
