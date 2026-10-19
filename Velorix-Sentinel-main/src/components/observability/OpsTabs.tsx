import { Link, useRouterState } from "@tanstack/react-router";

import { cn } from "@/lib/utils";

export type OpsTabTo =
  | "/ops"
  | "/ops/metrics"
  | "/ops/logs"
  | "/ops/api"
  | "/ops/database"
  | "/ops/deployments"
  | "/ops/environments"
  | "/ops/backups"
  | "/ops/alerts"
  | "/ops/dependencies"
  | "/ops/devsecops"
  | "/ops/settings"
  | "/ops/docs";

const TABS: { to: OpsTabTo; label: string; exact?: boolean }[] = [
  { to: "/ops", label: "Operations", exact: true },
  { to: "/ops/metrics", label: "Metrics" },
  { to: "/ops/logs", label: "Logs" },
  { to: "/ops/api", label: "API Monitoring" },
  { to: "/ops/database", label: "Database" },
  { to: "/ops/deployments", label: "Deployments" },
  { to: "/ops/environments", label: "Environments" },
  { to: "/ops/backups", label: "Backup & Recovery" },
  { to: "/ops/alerts", label: "Alerting" },
  { to: "/ops/dependencies", label: "Dependencies" },
  { to: "/ops/devsecops", label: "DevSecOps" },
  { to: "/ops/settings", label: "Settings" },
  { to: "/ops/docs", label: "Documentation" },
];

/** Horizontal section switcher for the Platform Observability Center. */
export function OpsTabs() {
  const pathname = useRouterState({ select: (state) => state.location.pathname });

  return (
    <nav aria-label="Observability sections" className="-mx-1 overflow-x-auto">
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
