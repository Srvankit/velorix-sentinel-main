import { createFileRoute } from "@tanstack/react-router";

import { AdminSection, PolicyStateBadge } from "@/components/admin";
import { Button } from "@/components/ui/button";
import { SECURITY_POLICY_CATALOGUE } from "@/data/admin-reference";
import { ADMIN_WRITE_READY } from "@/services/adminService";

export const Route = createFileRoute("/admin/policies")({
  head: () => ({
    meta: [
      { title: "Security Policies · Velorix Sentinel" },
      {
        name: "description",
        content: "Password, session, MFA and network access policy configuration.",
      },
      { property: "og:title", content: "Security Policies · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Password, session, MFA and network access policy configuration.",
      },
    ],
  }),
  component: PoliciesPage,
});

const CATEGORY_LABEL: Record<string, string> = {
  IDENTITY: "Identity",
  SESSION: "Session",
  DEVICE: "Device",
  NETWORK: "Network",
  APPLICATION: "Application",
};

function PoliciesPage() {
  const grouped = SECURITY_POLICY_CATALOGUE.reduce<
    Record<string, typeof SECURITY_POLICY_CATALOGUE>
  >((accumulator, policy) => {
    (accumulator[policy.category] ??= []).push(policy);
    return accumulator;
  }, {});

  return (
    <div className="space-y-4">
      {Object.entries(grouped).map(([category, policies]) => (
        <AdminSection
          key={category}
          title={`${CATEGORY_LABEL[category] ?? category} Policies`}
          description="Enforcement state is read from the administration backend; nothing is assumed to be on."
        >
          <div className="space-y-2.5">
            {policies.map((policy) => (
              <div
                key={policy.id}
                className="flex flex-wrap items-start justify-between gap-3 rounded-xl border border-border/70 bg-muted/10 px-3.5 py-3"
              >
                <div className="min-w-0 max-w-2xl">
                  <p className="text-xs font-semibold">{policy.name}</p>
                  <p className="mt-0.5 text-[11px] text-muted-foreground">{policy.description}</p>
                  <p className="mt-1.5 text-[10px] tracking-wide text-muted-foreground/80 uppercase">
                    Applies to · {policy.appliesTo}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <PolicyStateBadge state={policy.state} />
                  <Button size="sm" variant="outline" disabled={!ADMIN_WRITE_READY}>
                    Configure
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </AdminSection>
      ))}
    </div>
  );
}
