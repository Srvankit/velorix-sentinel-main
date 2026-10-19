import { Link } from "@tanstack/react-router";
import { Bell, LogOut, Menu, Moon, PanelLeftClose, PanelLeftOpen, Sun, X } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";

import { Logo } from "@/components/common";
import { GlobalSearch } from "@/components/soc/GlobalSearch";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";
import { useDashboardSummary } from "@/hooks/useDashboard";
import { cn } from "@/lib/utils";
import { getInitials } from "@/utils/format";
import { navigation } from "./navigation";

interface DashboardLayoutProps {
  children: ReactNode;
}

/** Live inspection queue indicator, sourced from the dashboard summary endpoint. */
function QueueStatus() {
  const { data, isLoading } = useDashboardSummary();
  if (isLoading || !data) return null;

  const running = data.runningAnalyses;
  return (
    <span
      className={cn(
        "hidden items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium md:inline-flex",
        running > 0
          ? "border-warning/40 bg-warning/10 text-warning"
          : "border-success/40 bg-success/10 text-success",
      )}
      title="Inspection queue"
    >
      <span className={cn("size-1.5 rounded-full bg-current", running > 0 && "animate-pulse")} />
      {running > 0 ? `${running} inspection${running === 1 ? "" : "s"} running` : "Queue idle"}
    </span>
  );
}

function ThemeToggle() {
  const [dark, setDark] = useState(true);

  useEffect(() => {
    const stored = window.localStorage.getItem("velorix.theme");
    if (stored) setDark(stored === "dark");
  }, []);

  useEffect(() => {
    document.documentElement.classList.toggle("dark", dark);
    window.localStorage.setItem("velorix.theme", dark ? "dark" : "light");
  }, [dark]);

  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={dark ? "Switch to light theme" : "Switch to dark theme"}
      onClick={() => setDark((value) => !value)}
    >
      {dark ? <Sun className="size-4" /> : <Moon className="size-4" />}
    </Button>
  );
}

/**
 * Application shell for all authenticated screens: collapsible sidebar on desktop,
 * slide-over navigation on mobile, sticky top bar with search, queue status and user.
 */
export function DashboardLayout({ children }: DashboardLayoutProps) {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const { user, logout, hasRole } = useAuth();

  const sidebar = (compact: boolean) => (
    <nav aria-label="Main" className="flex h-full flex-col gap-6 p-4">
      <div className={cn("flex items-center", compact ? "justify-center" : "justify-between")}>
        {compact ? <Logo showWordmark={false} /> : <Logo className="px-2 py-1" />}
      </div>

      <div className="flex-1 space-y-6 overflow-y-auto">
        {navigation.map((section) => (
          <div key={section.label} className="space-y-1">
            {!compact && (
              <p className="px-3 text-xs font-medium tracking-wider text-muted-foreground uppercase">
                {section.label}
              </p>
            )}
            {section.items
              .filter((item) => !item.roles || hasRole(...item.roles))
              .map((item) => (
                <Link
                  key={item.label}
                  to={item.to}
                  onClick={() => setMobileNavOpen(false)}
                  title={compact ? item.label : undefined}
                  className={cn(
                    "focus-ring flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-sidebar-foreground transition-colors hover:bg-sidebar-accent",
                    compact && "justify-center px-2",
                  )}
                  activeProps={{
                    className: "bg-sidebar-accent text-sidebar-accent-foreground font-medium",
                  }}
                >
                  <item.icon className="size-4 shrink-0" aria-hidden="true" />
                  {!compact && item.label}
                </Link>
              ))}
          </div>
        ))}
      </div>

      {user && (
        <div
          className={cn(
            "flex items-center gap-3 rounded-xl border border-sidebar-border p-3",
            compact && "flex-col gap-2 p-2",
          )}
        >
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-gradient-brand text-xs font-semibold text-primary-foreground">
            {getInitials(user.fullName)}
          </span>
          {!compact && (
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">{user.fullName}</span>
              <span className="block truncate text-xs text-muted-foreground">{user.email}</span>
            </span>
          )}
          <Button variant="ghost" size="icon" aria-label="Sign out" onClick={() => void logout()}>
            <LogOut className="size-4" />
          </Button>
        </div>
      )}
    </nav>
  );

  return (
    <div className="min-h-dvh bg-background">
      <aside
        className={cn(
          "fixed inset-y-0 left-0 hidden border-r border-sidebar-border bg-sidebar transition-[width] duration-200 lg:block",
          collapsed ? "w-20" : "w-64",
        )}
      >
        {sidebar(collapsed)}
      </aside>

      {mobileNavOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            aria-label="Close navigation"
            className="absolute inset-0 bg-background/80 backdrop-blur-sm"
            onClick={() => setMobileNavOpen(false)}
          />
          <aside className="relative h-full w-72 border-r border-sidebar-border bg-sidebar">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Close navigation"
              className="absolute top-3 right-3"
              onClick={() => setMobileNavOpen(false)}
            >
              <X className="size-4" />
            </Button>
            {sidebar(false)}
          </aside>
        </div>
      )}

      <div className={cn("transition-[padding] duration-200", collapsed ? "lg:pl-20" : "lg:pl-64")}>
        <header className="sticky top-0 z-40 flex h-16 items-center gap-3 border-b border-border bg-background/80 px-4 backdrop-blur-xl sm:px-6">
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            aria-label="Open navigation"
            onClick={() => setMobileNavOpen(true)}
          >
            <Menu className="size-4" />
          </Button>

          <Button
            variant="ghost"
            size="icon"
            className="hidden lg:inline-flex"
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            onClick={() => setCollapsed((value) => !value)}
          >
            {collapsed ? (
              <PanelLeftOpen className="size-4" />
            ) : (
              <PanelLeftClose className="size-4" />
            )}
          </Button>

          <div className="hidden max-w-md flex-1 sm:block">
            <GlobalSearch />
          </div>

          <div className="ml-auto flex items-center gap-2">
            <QueueStatus />
            <ThemeToggle />
            <Button variant="ghost" size="icon" aria-label="Notifications" asChild>
              <Link to="/notifications">
                <Bell className="size-4" />
              </Link>
            </Button>
            {user && (
              <span className="hidden items-center gap-2 rounded-full border border-border py-1 pr-3 pl-1 sm:inline-flex">
                <span className="grid size-7 place-items-center rounded-full bg-gradient-brand text-[10px] font-semibold text-primary-foreground">
                  {getInitials(user.fullName)}
                </span>
                <span className="max-w-32 truncate text-xs font-medium">{user.fullName}</span>
              </span>
            )}
          </div>
        </header>

        <main className="mx-auto w-full max-w-[100rem] px-4 py-8 sm:px-6">{children}</main>
      </div>
    </div>
  );
}
