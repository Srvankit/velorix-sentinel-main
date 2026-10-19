import { createFileRoute } from "@tanstack/react-router";
import { Fingerprint, ShieldCheck } from "lucide-react";

import { AdminKpi, AdminSection, AwaitingAdminData } from "@/components/admin";
import { IDENTITY_CAPABILITIES } from "@/data/admin-reference";
import { useAdminResource } from "@/hooks/useAdminResource";
import { adminService } from "@/services/adminService";

export const Route = createFileRoute("/admin/security")({
  head: () => ({
    meta: [
      { title: "Enterprise Security · Velorix Sentinel" },
      {
        name: "description",
        content: "SSO, SAML, SCIM provisioning and Zero Trust enforcement settings.",
      },
      { property: "og:title", content: "Enterprise Security · Velorix Sentinel" },
      {
        property: "og:description",
        content: "SSO, SAML, SCIM provisioning and Zero Trust enforcement settings.",
      },
    ],
  }),
  component: EnterpriseSecurityPage,
});

function EnterpriseSecurityPage() {
  const mfa = useAdminResource(adminService.getMfaOverview);
  const providers = useAdminResource(adminService.listIdentityProviders);

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <AdminKpi
          label="MFA Enrolled"
          icon={Fingerprint}
          value={mfa.data?.enrolledUsers ?? null}
          isLoading={mfa.status === "loading"}
          hint="Identities with a registered second factor"
        />
        <AdminKpi
          label="MFA Enforcement"
          icon={ShieldCheck}
          value={
            mfa.data?.enforcement && mfa.data.enforcement !== "UNKNOWN"
              ? mfa.data.enforcement
              : null
          }
          isLoading={mfa.status === "loading"}
          hint="Tenant-wide enforcement tier"
        />
        <AdminKpi
          label="Identity Providers"
          icon={Fingerprint}
          value={providers.data?.length ?? null}
          isLoading={providers.status === "loading"}
          hint="Federated IdPs configured"
        />
        <AdminKpi
          label="Zero Trust Posture"
          icon={ShieldCheck}
          value={null}
          hint="Continuous verification coverage"
        />
      </div>

      <AdminSection
        title="Enterprise Identity Capabilities"
        description="Federation, provisioning and session governance surfaces on the roadmap."
      >
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          {IDENTITY_CAPABILITIES.map((capability) => (
            <article
              key={capability.id}
              className="rounded-2xl border border-border/70 bg-card/40 p-4 backdrop-blur-xl transition-colors hover:border-primary/30"
            >
              <h3 className="text-sm font-semibold">{capability.name}</h3>
              <p className="mt-1 text-xs text-muted-foreground">{capability.summary}</p>
              <p className="mt-3 text-[10px] tracking-wide text-muted-foreground/80 uppercase">
                Enterprise-ready · Awaiting backend
              </p>
            </article>
          ))}
        </div>
      </AdminSection>

      <div className="grid gap-4 lg:grid-cols-2">
        <AdminSection
          title="Identity Providers"
          description="SAML, OIDC and SCIM connections with sync health."
        >
          <AwaitingAdminData detail="Configured providers, mapped domains and last sync times load from the administration backend." />
        </AdminSection>
        <AdminSection
          title="Session Policies"
          description="Continuous evaluation, re-authentication and revocation rules."
        >
          <AwaitingAdminData detail="Session governance rules are defined server-side; nothing is assumed to be enforced." />
        </AdminSection>
      </div>
    </div>
  );
}
