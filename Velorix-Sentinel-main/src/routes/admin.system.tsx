import { createFileRoute } from "@tanstack/react-router";

import { AdminSection, AwaitingAdminData } from "@/components/admin";
import { SYSTEM_CONFIG_SECTIONS } from "@/data/admin-reference";
import { useAdminResource } from "@/hooks/useAdminResource";
import { adminService } from "@/services/adminService";
import { apiConfig } from "@/utils/apiConfig";
import { APP_NAME, COMPANY_NAME } from "@/utils/constants";

export const Route = createFileRoute("/admin/system")({
  head: () => ({
    meta: [
      { title: "System Configuration · Velorix Sentinel" },
      {
        name: "description",
        content: "Environment configuration, branding and platform-wide defaults.",
      },
      { property: "og:title", content: "System Configuration · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Environment configuration, branding and platform-wide defaults.",
      },
    ],
  }),
  component: SystemConfigurationPage,
});

function SystemConfigurationPage() {
  const configuration = useAdminResource(adminService.getSystemConfiguration);

  return (
    <div className="space-y-4">
      <AdminSection
        title="Environment"
        description="Runtime configuration resolved by this frontend deployment."
      >
        <dl className="grid gap-2 md:grid-cols-2">
          <Field label="Platform" value={`${APP_NAME} · ${COMPANY_NAME}`} />
          <Field label="API base URL" value={apiConfig.baseUrl} />
          <Field label="Host context" value={apiConfig.isRemoteHost ? "Remote" : "Local"} />
          <Field
            label="Backend environment"
            value={
              configuration.data?.environment && configuration.data.environment !== "UNKNOWN"
                ? configuration.data.environment
                : "Awaiting Live Data"
            }
          />
        </dl>
      </AdminSection>

      <AdminSection
        title="System Configuration"
        description="Tenant-wide platform settings, delivered by the administration backend."
      >
        <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
          {SYSTEM_CONFIG_SECTIONS.map((section) => (
            <div
              key={section.id}
              className="rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5"
            >
              <p className="text-xs font-medium">{section.name}</p>
              <p className="mt-0.5 text-[11px] text-muted-foreground">{section.summary}</p>
              <p className="mt-2 text-[10px] tracking-wide text-muted-foreground/80 uppercase">
                Awaiting Live Data
              </p>
            </div>
          ))}
        </div>
      </AdminSection>

      <AdminSection
        title="Maintenance Mode"
        description="Scheduled maintenance windows and operator banner."
      >
        <AwaitingAdminData detail="Maintenance state is owned by the backend so every client observes the same window." />
      </AdminSection>
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border border-border/70 bg-muted/10 px-3 py-2 text-xs">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="truncate font-mono text-[11px] font-medium">{value}</dd>
    </div>
  );
}
