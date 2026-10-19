import { useNavigate } from "@tanstack/react-router";
import { Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { useAuth } from "@/hooks/useAuth";
import { cn } from "@/lib/utils";
import { navigation } from "@/layouts/navigation";

interface SearchEntry {
  group: string;
  label: string;
  keywords: string;
  to: string;
  hash?: string;
}

const DASHBOARD_SECTIONS: SearchEntry[] = [
  {
    group: "Dashboard",
    label: "Executive security overview",
    keywords: "score posture risk",
    to: "/dashboard",
  },
  {
    group: "Dashboard",
    label: "Security KPIs",
    keywords: "metrics threats packets kpi",
    to: "/dashboard",
  },
  {
    group: "Dashboard",
    label: "Live threat feed",
    keywords: "alerts detections severity",
    to: "/dashboard",
  },
  {
    group: "Dashboard",
    label: "Security timeline",
    keywords: "events history platform",
    to: "/dashboard",
  },
  { group: "Dashboard", label: "System health", keywords: "api database worker", to: "/dashboard" },
  {
    group: "Dashboard",
    label: "Operations center",
    keywords: "render neon environment version",
    to: "/dashboard",
  },
];

const SETTINGS_SECTIONS: SearchEntry[] = [
  { group: "Settings", label: "Profile settings", keywords: "name email avatar", to: "/settings" },
  {
    group: "Settings",
    label: "Security settings",
    keywords: "password mfa sessions",
    to: "/settings",
  },
  { group: "Settings", label: "Notifications", keywords: "email alerts digest", to: "/settings" },
  { group: "Settings", label: "Appearance", keywords: "theme dark light density", to: "/settings" },
  { group: "Settings", label: "Sessions", keywords: "devices tokens revoke", to: "/settings" },
  { group: "Settings", label: "API keys", keywords: "tokens integration", to: "/settings" },
  { group: "Settings", label: "Workspace", keywords: "organisation retention", to: "/settings" },
  { group: "Settings", label: "About", keywords: "version build support", to: "/settings" },
];

/**
 * Enterprise command palette. Today it resolves navigation, dashboard sections
 * and settings; the same surface will host federated search later.
 */
export function GlobalSearch({ className }: { className?: string }) {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { hasRole } = useAuth();

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key.toLowerCase() === "k" && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        setOpen((value) => !value);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const navEntries = useMemo<SearchEntry[]>(
    () =>
      navigation.flatMap((section) =>
        section.items
          .filter((item) => !item.roles || hasRole(...item.roles))
          .map((item) => ({
            group: "Navigation",
            label: item.label,
            keywords: section.label,
            to: item.to,
          })),
      ),
    [hasRole],
  );

  const groups = useMemo(() => {
    const all = [...navEntries, ...DASHBOARD_SECTIONS, ...SETTINGS_SECTIONS];
    return Array.from(new Set(all.map((entry) => entry.group))).map((group) => ({
      group,
      entries: all.filter((entry) => entry.group === group),
    }));
  }, [navEntries]);

  const go = (to: string) => {
    setOpen(false);
    void navigate({ to });
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Open global search"
        className={cn(
          "focus-ring group flex h-9 w-full items-center gap-2 rounded-lg border border-input bg-surface px-3 text-sm text-muted-foreground transition-colors hover:border-primary/30 hover:text-foreground",
          className,
        )}
      >
        <Search className="size-4 shrink-0" aria-hidden="true" />
        <span className="truncate">Search dashboard, navigation, settings…</span>
        <kbd className="ms-auto hidden shrink-0 rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[10px] md:inline">
          ⌘K
        </kbd>
      </button>

      <CommandDialog open={open} onOpenChange={setOpen}>
        <CommandInput placeholder="Search sections, pages and settings…" />
        <CommandList>
          <CommandEmpty>No matches found.</CommandEmpty>
          {groups.map(({ group, entries }) => (
            <CommandGroup key={group} heading={group}>
              {entries.map((entry) => (
                <CommandItem
                  key={`${group}-${entry.label}`}
                  value={`${entry.label} ${entry.keywords}`}
                  onSelect={() => go(entry.to)}
                >
                  {entry.label}
                </CommandItem>
              ))}
            </CommandGroup>
          ))}
        </CommandList>
      </CommandDialog>
    </>
  );
}
