import { Link, useRouterState } from "@tanstack/react-router";

import { cn } from "@/lib/utils";

type GovernanceTabTo =
  | "/reports"
  | "/reports/library"
  | "/reports/compliance"
  | "/reports/risk"
  | "/reports/audit"
  | "/reports/analytics"
  | "/reports/board"
  | "/reports/builder"
  | "/reports/exports"
  | "/reports/knowledge";

const TABS: { to: GovernanceTabTo; label: string; exact?: boolean }[] = [
  { to: "/reports", label: "Executive", exact: true },
  { to: "/reports/library", label: "Reports" },
  { to: "/reports/compliance", label: "Compliance" },
  { to: "/reports/risk", label: "Risk" },
  { to: "/reports/audit", label: "Audit" },
  { to: "/reports/analytics", label: "Analytics" },
  { to: "/reports/board", label: "Board View" },
  { to: "/reports/builder", label: "Builder" },
  { to: "/reports/exports", label: "Exports" },
  { to: "/reports/knowledge", label: "Knowledge" },
];

/** Horizontal section switcher for the Executive Reporting & Compliance Center. */
export function GovernanceTabs() {
  const pathname = useRouterState({ select: (state) => state.location.pathname });

  return (
    <nav aria-label="Governance sections" className="-mx-1 overflow-x-auto">
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
