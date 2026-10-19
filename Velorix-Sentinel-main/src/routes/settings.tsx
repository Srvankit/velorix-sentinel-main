import { createFileRoute } from "@tanstack/react-router";
import {
  Bell,
  Building2,
  Info,
  KeyRound,
  Lock,
  MonitorSmartphone,
  Palette,
  UserRound,
} from "lucide-react";
import { useEffect, useState } from "react";

import { ProtectedRoute } from "@/components/auth";
import { PageHeader, StatusBadge } from "@/components/common";
import { UserProfilePanel } from "@/components/soc";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useAuth } from "@/hooks/useAuth";
import { DashboardLayout } from "@/layouts";
import { cn } from "@/lib/utils";
import { APP_NAME, COMPANY_NAME } from "@/utils/constants";
import { apiConfig } from "@/utils/apiConfig";
import { formatDateTime } from "@/utils/format";

export const Route = createFileRoute("/settings")({
  component: SettingsPage,
  head: () => ({
    meta: [
      { title: `Settings · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Manage your profile, security, notifications, appearance, sessions and workspace preferences in Velorix Sentinel.",
      },
      { property: "og:title", content: `Settings · ${APP_NAME}` },
      {
        property: "og:description",
        content: "Profile, security, notifications, appearance, sessions and workspace controls.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function Panel({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <section className="glass-panel rounded-2xl p-6">
      <header className="mb-5">
        <h2 className="font-display text-base font-semibold tracking-tight">{title}</h2>
        <p className="text-sm text-muted-foreground">{description}</p>
      </header>
      {children}
    </section>
  );
}

function ToggleRow({
  label,
  description,
  defaultChecked = false,
  disabled = false,
}: {
  label: string;
  description: string;
  defaultChecked?: boolean;
  disabled?: boolean;
}) {
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 border-b border-border/50 py-3 last:border-0">
      <div className="min-w-0">
        <p className="text-sm font-medium">{label}</p>
        <p className="text-xs text-muted-foreground">{description}</p>
      </div>
      <Switch defaultChecked={defaultChecked} disabled={disabled} aria-label={label} />
    </div>
  );
}

function ComingSoon({ note }: { note: string }) {
  return (
    <p className="rounded-xl border border-dashed border-border bg-muted/20 px-4 py-6 text-center text-sm text-muted-foreground">
      {note}
    </p>
  );
}

function AppearanceSettings() {
  const [dark, setDark] = useState(true);

  useEffect(() => {
    const stored = window.localStorage.getItem("velorix.theme");
    if (stored) setDark(stored === "dark");
  }, []);

  const apply = (next: boolean) => {
    setDark(next);
    document.documentElement.classList.toggle("dark", next);
    window.localStorage.setItem("velorix.theme", next ? "dark" : "light");
  };

  return (
    <Panel title="Appearance" description="Theme and interface density for this browser.">
      <div className="grid gap-3 sm:grid-cols-2">
        {[
          { label: "Dark (SOC default)", value: true },
          { label: "Light", value: false },
        ].map((option) => (
          <button
            key={option.label}
            type="button"
            onClick={() => apply(option.value)}
            className={cn(
              "focus-ring rounded-xl border p-4 text-left transition-all hover:-translate-y-0.5",
              dark === option.value
                ? "border-primary/50 bg-primary/5"
                : "border-border bg-surface/40",
            )}
          >
            <span className="text-sm font-medium">{option.label}</span>
            <span className="mt-3 flex gap-1.5">
              <span
                className={cn(
                  "h-8 flex-1 rounded",
                  option.value ? "bg-background" : "bg-foreground/90",
                )}
              />
              <span className="h-8 w-6 rounded bg-primary" />
            </span>
          </button>
        ))}
      </div>
    </Panel>
  );
}

function SettingsPage() {
  const { user } = useAuth();

  return (
    <ProtectedRoute>
      <DashboardLayout>
        <PageHeader
          title="Settings"
          description="Account, security and workspace preferences for Velorix Sentinel."
        />

        <Tabs defaultValue="profile" className="mt-6">
          <div className="-mx-1 overflow-x-auto px-1 pb-1">
            <TabsList className="inline-flex w-max">
              <TabsTrigger value="profile">
                <UserRound className="size-4" /> Profile
              </TabsTrigger>
              <TabsTrigger value="security">
                <Lock className="size-4" /> Security
              </TabsTrigger>
              <TabsTrigger value="notifications">
                <Bell className="size-4" /> Notifications
              </TabsTrigger>
              <TabsTrigger value="appearance">
                <Palette className="size-4" /> Appearance
              </TabsTrigger>
              <TabsTrigger value="sessions">
                <MonitorSmartphone className="size-4" /> Sessions
              </TabsTrigger>
              <TabsTrigger value="api-keys">
                <KeyRound className="size-4" /> API keys
              </TabsTrigger>
              <TabsTrigger value="workspace">
                <Building2 className="size-4" /> Workspace
              </TabsTrigger>
              <TabsTrigger value="about">
                <Info className="size-4" /> About
              </TabsTrigger>
            </TabsList>
          </div>

          <TabsContent value="profile" className="mt-6">
            <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
              <Panel title="Profile" description="Identity details tied to your analyst account.">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1.5">
                    <Label htmlFor="firstName">First name</Label>
                    <Input id="firstName" defaultValue={user?.firstName} readOnly />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="lastName">Last name</Label>
                    <Input id="lastName" defaultValue={user?.lastName} readOnly />
                  </div>
                  <div className="space-y-1.5 sm:col-span-2">
                    <Label htmlFor="email">Email</Label>
                    <Input id="email" defaultValue={user?.email} readOnly />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="role">Role</Label>
                    <Input id="role" defaultValue={user?.role} readOnly />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="since">Member since</Label>
                    <Input id="since" defaultValue={formatDateTime(user?.createdAt)} readOnly />
                  </div>
                </div>
                <p className="mt-4 text-xs text-muted-foreground">
                  Profile editing endpoints are not exposed yet — fields are read-only.
                </p>
              </Panel>
              <UserProfilePanel />
            </div>
          </TabsContent>

          <TabsContent value="security" className="mt-6">
            <Panel title="Security" description="Credentials and account protection.">
              <div className="space-y-1">
                <ToggleRow
                  label="Multi-factor authentication"
                  description="Time-based one-time passcodes — backend support pending."
                  disabled
                />
                <ToggleRow
                  label="Alert on new sign-in"
                  description="Email me whenever a new device authenticates."
                />
                <ToggleRow
                  label="Force sign-out on password change"
                  description="Revoke every refresh token when credentials rotate."
                  defaultChecked
                />
              </div>
              <div className="mt-5 flex flex-wrap gap-2">
                <Button variant="outline" size="sm" disabled>
                  Change password
                </Button>
                <Button variant="outline" size="sm" disabled>
                  Download recovery codes
                </Button>
              </div>
            </Panel>
          </TabsContent>

          <TabsContent value="notifications" className="mt-6">
            <Panel title="Notifications" description="Choose which detections reach your inbox.">
              <div className="space-y-1">
                <ToggleRow
                  label="Critical threat alerts"
                  description="Immediate email for CRITICAL severity findings."
                  defaultChecked
                />
                <ToggleRow
                  label="High severity digest"
                  description="Hourly roll-up of HIGH severity detections."
                  defaultChecked
                />
                <ToggleRow
                  label="Inspection completed"
                  description="Notify me when a capture finishes analysis."
                />
                <ToggleRow
                  label="Weekly executive summary"
                  description="Posture report every Monday morning."
                />
              </div>
            </Panel>
          </TabsContent>

          <TabsContent value="appearance" className="mt-6">
            <AppearanceSettings />
          </TabsContent>

          <TabsContent value="sessions" className="mt-6">
            <Panel title="Sessions" description="Devices holding a valid refresh token.">
              <ul className="space-y-2">
                <li className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 rounded-xl border border-border/70 bg-surface/40 px-4 py-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">This browser</p>
                    <p className="truncate text-xs text-muted-foreground">
                      Active session · {user?.email}
                    </p>
                  </div>
                  <StatusBadge status="CURRENT" tone="success" />
                </li>
              </ul>
              <p className="mt-4 text-xs text-muted-foreground">
                A session inventory endpoint will list every device once available.
              </p>
            </Panel>
          </TabsContent>

          <TabsContent value="api-keys" className="mt-6">
            <Panel title="API keys" description="Programmatic access to the Sentinel API.">
              <ComingSoon note="Coming soon — API key issuance and rotation are not enabled yet." />
            </Panel>
          </TabsContent>

          <TabsContent value="workspace" className="mt-6">
            <Panel title="Workspace" description="Organisation-wide platform configuration.">
              <dl className="grid gap-3 sm:grid-cols-2">
                {[
                  ["Organisation", COMPANY_NAME],
                  ["Product", APP_NAME],
                  ["API endpoint", apiConfig.baseUrl],
                  ["Environment", import.meta.env.MODE === "production" ? "Production" : "Preview"],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-xl border border-border/70 bg-surface/40 p-4">
                    <dt className="text-xs text-muted-foreground">{label}</dt>
                    <dd className="mt-1 truncate font-mono text-sm" title={value}>
                      {value}
                    </dd>
                  </div>
                ))}
              </dl>
            </Panel>
          </TabsContent>

          <TabsContent value="about" className="mt-6">
            <Panel title="About" description={`${APP_NAME} by ${COMPANY_NAME}`}>
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between border-b border-border/50 pb-2">
                  <dt className="text-muted-foreground">Version</dt>
                  <dd className="font-mono">v1.0.0</dd>
                </div>
                <div className="flex justify-between border-b border-border/50 pb-2">
                  <dt className="text-muted-foreground">Detection rules</dt>
                  <dd className="font-mono">13 active</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Support</dt>
                  <dd className="font-mono">support@velorix.tech</dd>
                </div>
              </dl>
            </Panel>
          </TabsContent>
        </Tabs>
      </DashboardLayout>
    </ProtectedRoute>
  );
}
