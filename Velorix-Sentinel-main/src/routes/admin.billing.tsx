import { createFileRoute } from "@tanstack/react-router";
import { CreditCard, FileText, HardDrive, Users } from "lucide-react";

import { AdminKpi, AdminSection, EnterpriseOnly } from "@/components/admin";
import { ADMIN_BILLING_READY } from "@/services/adminService";

export const Route = createFileRoute("/admin/billing")({
  head: () => ({
    meta: [
      { title: "Billing & Subscription · Velorix Sentinel" },
      {
        name: "description",
        content:
          "Review subscription tier, seat allocation and invoicing for your Velorix Sentinel tenant.",
      },
      { property: "og:title", content: "Billing & Subscription · Velorix Sentinel" },
      {
        property: "og:description",
        content:
          "Review subscription tier, seat allocation and invoicing for your Velorix Sentinel tenant.",
      },
    ],
  }),
  component: BillingPage,
});

const PLANS = [
  {
    name: "Community",
    summary: "Single analyst, local captures, core detection rules.",
  },
  {
    name: "Professional",
    summary: "Team workspaces, scheduled reporting, threat intel enrichment.",
  },
  {
    name: "Enterprise",
    summary: "SSO/SCIM, unlimited workspaces, compliance evidence, dedicated support.",
  },
];

function BillingPage() {
  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <AdminKpi
          label="Current Plan"
          icon={CreditCard}
          value={null}
          hint="Provisioned per tenant"
        />
        <AdminKpi
          label="License Count"
          icon={Users}
          value={null}
          hint="Seats consumed vs entitlement"
        />
        <AdminKpi
          label="Storage"
          icon={HardDrive}
          value={null}
          hint="Capture storage against quota"
        />
        <AdminKpi label="Invoices" icon={FileText} value={null} hint="Issued billing documents" />
      </div>

      <AdminSection
        title="Subscription & Billing"
        description="Plan, usage, invoices and payment method."
      >
        <EnterpriseOnly />
      </AdminSection>

      <AdminSection
        title="Upgrade Plans"
        description="Editions available for Velorix Sentinel deployments."
      >
        <div className="grid gap-3 md:grid-cols-3">
          {PLANS.map((plan) => (
            <article
              key={plan.name}
              className="rounded-2xl border border-border/70 bg-card/40 p-4 backdrop-blur-xl"
            >
              <h3 className="text-sm font-semibold">{plan.name}</h3>
              <p className="mt-1 text-xs text-muted-foreground">{plan.summary}</p>
              <p className="mt-3 text-[11px] font-medium text-primary">
                {ADMIN_BILLING_READY ? "Contact sales" : "Available in Enterprise Edition"}
              </p>
            </article>
          ))}
        </div>
      </AdminSection>

      <AdminSection title="Billing History" description="Invoices, payments and credit notes.">
        <EnterpriseOnly
          title="Available in Enterprise Edition"
          detail="Billing history is issued by the commerce backend. No invoice records exist for this deployment."
        />
      </AdminSection>
    </div>
  );
}
