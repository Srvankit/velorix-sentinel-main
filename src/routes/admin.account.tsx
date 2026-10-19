import { createFileRoute } from "@tanstack/react-router";

import { AdminSection, AwaitingAdminData } from "@/components/admin";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { ADMIN_WRITE_READY } from "@/services/adminService";
import { useAuth } from "@/hooks/useAuth";

export const Route = createFileRoute("/admin/account")({
  head: () => ({
    meta: [
      { title: "Account Settings · Velorix Sentinel" },
      {
        name: "description",
        content:
          "Manage the organisation account owner, contacts and tenant lifecycle for Velorix Sentinel.",
      },
      { property: "og:title", content: "Account Settings · Velorix Sentinel" },
      {
        property: "og:description",
        content:
          "Manage the organisation account owner, contacts and tenant lifecycle for Velorix Sentinel.",
      },
    ],
  }),
  component: AccountSettingsPage,
});

const TOGGLES = [
  { id: "email-digest", label: "Daily email digest", detail: "Summary of detections and cases." },
  {
    id: "critical-alerts",
    label: "Critical alert notifications",
    detail: "Immediate delivery for severity CRITICAL.",
  },
  {
    id: "reduced-motion",
    label: "Reduced motion",
    detail: "Minimise animation across the console.",
  },
  {
    id: "high-contrast",
    label: "High contrast",
    detail: "Increase contrast for low-vision accessibility.",
  },
];

const SELECTS = [
  { label: "Language", value: "English (United States)" },
  { label: "Time Zone", value: "Awaiting Live Data" },
  { label: "Date format", value: "ISO 8601 (YYYY-MM-DD)" },
  { label: "Density", value: "Comfortable" },
];

function AccountSettingsPage() {
  const { user } = useAuth();

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <AdminSection title="Profile" description="Identity details for the signed-in administrator.">
        <dl className="space-y-2 text-xs">
          <Field label="Name" value={user ? `${user.firstName} ${user.lastName}` : "—"} />
          <Field label="Email" value={user?.email ?? "—"} />
          <Field label="Role" value={user?.role ?? "—"} />
          <Field label="Account status" value={user?.active ? "Active" : "Awaiting Live Data"} />
        </dl>
      </AdminSection>

      <AdminSection
        title="Preferences"
        description="Regional, appearance and density settings for this account."
      >
        <dl className="space-y-2 text-xs">
          {SELECTS.map((item) => (
            <Field key={item.label} label={item.label} value={item.value} />
          ))}
        </dl>
      </AdminSection>

      <AdminSection
        title="Notifications & Accessibility"
        description="Preference writes are enabled once the account settings API ships."
      >
        <div className="space-y-3">
          {TOGGLES.map((toggle) => (
            <div key={toggle.id} className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <Label htmlFor={toggle.id} className="text-xs font-medium">
                  {toggle.label}
                </Label>
                <p className="mt-0.5 text-[11px] text-muted-foreground">{toggle.detail}</p>
              </div>
              <Switch id={toggle.id} disabled={!ADMIN_WRITE_READY} />
            </div>
          ))}
        </div>
      </AdminSection>

      <AdminSection
        title="Appearance"
        description="Theme selection persists per account once preferences are stored server-side."
      >
        <AwaitingAdminData
          compact
          title="Dark theme enforced"
          detail="Velorix Sentinel ships a dark-first SOC theme. Light and system themes unlock with account preference storage."
        />
      </AdminSection>
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border border-border/70 bg-muted/10 px-3 py-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="truncate font-medium">{value}</dd>
    </div>
  );
}
