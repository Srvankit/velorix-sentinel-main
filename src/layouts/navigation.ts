import type { LucideIcon } from "lucide-react";
import {
  Activity,
  Bell,
  BookOpen,
  BotMessageSquare,
  FileBarChart,
  History,
  LayoutDashboard,
  Radar,
  Crosshair,
  Settings,
  Siren,
  TerminalSquare,
  ShieldAlert,
  ShieldCheck,
  UploadCloud,
  UserRound,
} from "lucide-react";

import type { UserRole } from "@/types/auth";

/**
 * Sidebar navigation registry.
 *
 * Each module adds its entry here when its route is created, so the shell and
 * the routes never drift apart.
 */
export interface NavItem {
  label: string;
  to:
    | "/dashboard"
    | "/upload"
    | "/analysis"
    | "/threats"
    | "/incidents"
    | "/soc"
    | "/intel"
    | "/assistant"
    | "/knowledge"
    | "/reports"
    | "/history"
    | "/notifications"
    | "/settings"
    | "/profile"
    | "/admin"
    | "/ops";
  icon: LucideIcon;
  /** Restrict visibility to specific roles; omit for all authenticated users. */
  roles?: UserRole[];
}

export interface NavSection {
  label: string;
  items: NavItem[];
}

export const navigation: NavSection[] = [
  {
    label: "Operations",
    items: [
      { label: "Dashboard", to: "/dashboard", icon: LayoutDashboard },
      { label: "Upload", to: "/upload", icon: UploadCloud },
      { label: "Analysis", to: "/analysis", icon: Radar },
      { label: "Threats", to: "/threats", icon: ShieldAlert },
      { label: "Incidents", to: "/incidents", icon: Siren },
      { label: "SOC Workspace", to: "/soc", icon: TerminalSquare },
    ],
  },
  {
    label: "Intelligence",
    items: [
      { label: "Threat Intelligence", to: "/intel", icon: Crosshair },
      { label: "AI Copilot", to: "/assistant", icon: BotMessageSquare },
      { label: "Knowledge", to: "/knowledge", icon: BookOpen },
      { label: "Reports", to: "/reports", icon: FileBarChart },
      { label: "History", to: "/history", icon: History },
    ],
  },
  {
    label: "Account",
    items: [
      { label: "Notifications", to: "/notifications", icon: Bell },
      { label: "Settings", to: "/settings", icon: Settings },
      { label: "Profile", to: "/profile", icon: UserRound },
      { label: "Administration", to: "/admin", icon: ShieldCheck, roles: ["ADMIN"] },
      { label: "Platform Observability", to: "/ops", icon: Activity, roles: ["ADMIN"] },
    ],
  },
];
