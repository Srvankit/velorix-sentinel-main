import { Link, useRouterState } from "@tanstack/react-router";

import { cn } from "@/lib/utils";

type AdminTabTo =
  | "/admin"
  | "/admin/organizations"
  | "/admin/users"
  | "/admin/roles"
  | "/admin/workspaces"
  | "/admin/policies"
  | "/admin/integrations"
  | "/admin/audit"
  | "/admin/sessions"
  | "/admin/account"
  | "/admin/billing"
  | "/admin/security"
  | "/admin/system";

const TABS: { to: AdminTabTo; label: string; exact?: boolean }[] = [
  { to: "/admin", label: "Overview", exact: true },
  { to: "/admin/organizations", label: "Organizations" },
  { to: "/admin/users", label: "Users" },
  { to: "/admin/roles", label: "Roles & Access" },
  { to: "/admin/workspaces", label: "Workspaces" },
  { to: "/admin/policies", label: "Policies" },
  { to: "/admin/integrations", label: "Integrations" },
  { to: "/admin/audit", label: "Audit Logs" },
  { to: "/admin/sessions", label: "Sessions" },
  { to: "/admin/security", label: "Enterprise Security" },
  { to: "/admin/account", label: "Account" },
  { to: "/admin/billing", label: "Billing" },
  { to: "/admin/system", label: "System" },
];

/** Horizontal section switcher for the Enterprise Administration Center. */
export function AdminTabs() {
  const pathname = useRouterState({ select: (state) => state.location.pathname });

  return (
    <nav aria-label="Administration sections" className="-mx-1 overflow-x-auto">
      <ul className="flex min-w-max items-center gap-1 px-1">
        {TABS.map((tab) => {
          const active = tab.exact
            ? pathname === tab.to || pathname === `${tab.to}/`
            : pathname.startsWith(tab.to);
          return (
            <li key={tab.to}>
              <Link
                to={tab.to}
                className={cn(
                  "inline-flex items-center rounded-lg px-3 py-1.5 text-xs font-medium whitespace-nowrap transition-colors",
                  active
                    ? "bg-primary/15 text-primary"
                    : "text-muted-foreground hover:bg-muted/40 hover:text-foreground",
                )}
              >
                {tab.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
