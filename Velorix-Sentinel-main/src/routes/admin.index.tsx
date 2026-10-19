import { createFileRoute } from "@tanstack/react-router";
import {
  Activity,
  Building2,
  Cable,
  CreditCard,
  Fingerprint,
  Gauge,
  Layers,
  ShieldCheck,
  Users,
  Waypoints,
} from "lucide-react";

import { AdminKpi, AdminSection, AwaitingAdminData } from "@/components/admin";
import { useAdminResource } from "@/hooks/useAdminResource";
import { adminService } from "@/services/adminService";

export const Route = createFileRoute("/admin/")({
  head: () => ({
    meta: [
      { title: "Administration · Velorix Sentinel" },
      {
        name: "description",
        content:
          "Central administration console for organisations, users, roles, policies and platform health.",
      },
      { property: "og:title", content: "Administration · Velorix Sentinel" },
      {
        property: "og:description",
        content:
          "Central administration console for organisations, users, roles, policies and platform health.",
      },
    ],
  }),
  component: AdminOverviewPage,
});

function AdminOverviewPage() {
  const overview = useAdminResource(adminService.getOverview);
  const activity = useAdminResource(adminService.listActivity);

  const data = overview.data;
  const loading = overview.status === "loading";

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        <AdminKpi
          label="Organizations"
          icon={Building2}
          value={data?.organizations ?? null}
          isLoading={loading}
          delay={0}
          hint="Tenants provisioned on this deployment"
        />
        <AdminKpi
          label="Active Users"
          icon={Users}
          value={data?.activeUsers ?? null}
          isLoading={loading}
          delay={0.03}
          hint="Identities active in the last 30 days"
        />
        <AdminKpi
          label="Security Policies"
          icon={ShieldCheck}
          value={data?.securityPolicies ?? null}
          isLoading={loading}
          delay={0.06}
          hint="Policies enforced across tenants"
        />
        <AdminKpi
          label="Workspaces"
          icon={Layers}
          value={data?.workspaces ?? null}
          isLoading={loading}
          delay={0.09}
          hint="Analysis workspaces assigned"
        />
        <AdminKpi
          label="Connected Services"
          icon={Cable}
          value={data?.connectedServices ?? null}
          isLoading={loading}
          delay={0.12}
          hint="Active integrations and connectors"
        />
        <AdminKpi
          label="Authentication Status"
          icon={Fingerprint}
          value={null}
          band={data?.authenticationHealth ?? "UNKNOWN"}
          isLoading={loading}
          delay={0.15}
          hint="Identity provider and MFA health"
        />
        <AdminKpi
          label="Platform Health"
          icon={Gauge}
          value={null}
          band={data?.platformHealth ?? "UNKNOWN"}
          isLoading={loading}
          delay={0.18}
          hint="API, worker and database availability"
        />
        <AdminKpi
          label="API Usage (24h)"
          icon={Waypoints}
          value={data?.apiRequests24h ?? null}
          isLoading={loading}
          delay={0.21}
          hint="Authenticated requests across all keys"
        />
        <AdminKpi
          label="License Status"
          icon={CreditCard}
          value={
            data?.licensesUsed != null && data?.licensesTotal != null
              ? `${data.licensesUsed}/${data.licensesTotal}`
              : null
          }
          isLoading={loading}
          delay={0.24}
          hint="Seats consumed against entitlement"
        />
        <AdminKpi
          label="Administrative Actions"
          icon={Activity}
          value={activity.data?.length ?? null}
          isLoading={activity.status === "loading"}
          delay={0.27}
          hint="Privileged changes in the last 24 hours"
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <AdminSection
          title="Recent Administrative Activity"
          description="Privileged configuration changes, role grants and tenant modifications."
        >
          <AwaitingAdminData
            title="Awaiting Live Data"
            detail="Administrative activity is written by the backend audit pipeline. No entries are simulated in this console."
          />
        </AdminSection>

        <AdminSection
          title="Platform Health"
          description="Availability of the services backing the administration console."
        >
          <div className="space-y-2">
            {["API Gateway", "Identity Service", "Database", "Analysis Workers", "Storage"].map(
              (service) => (
                <div
                  key={service}
                  className="flex items-center justify-between rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5"
                >
                  <span className="text-xs font-medium">{service}</span>
                  <span className="text-[11px] text-muted-foreground">Awaiting Live Data</span>
                </div>
              ),
            )}
          </div>
        </AdminSection>
      </div>
    </div>
  );
}
