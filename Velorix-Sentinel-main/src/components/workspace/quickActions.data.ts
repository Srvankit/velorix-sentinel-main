import { Ban, FileSearch, Flag, Forward, ShieldOff, Siren, UserPlus } from "lucide-react";
import type { LucideIcon } from "lucide-react";

export interface QuickAction {
  id: string;
  label: string;
  description: string;
  icon: LucideIcon;
  destructive?: boolean;
}

/** Response actions offered on a selected alert. All require the SOC backend. */
export const QUICK_ACTIONS: QuickAction[] = [
  {
    id: "acknowledge",
    label: "Acknowledge",
    description: "Claim the alert and move it to triaged.",
    icon: Flag,
  },
  {
    id: "assign",
    label: "Assign",
    description: "Route the alert to another analyst.",
    icon: UserPlus,
  },
  {
    id: "escalate",
    label: "Escalate",
    description: "Raise to incident response as a case.",
    icon: Siren,
  },
  {
    id: "investigate",
    label: "Investigate",
    description: "Open the full investigation workspace.",
    icon: FileSearch,
  },
  {
    id: "forward",
    label: "Forward",
    description: "Share with a downstream team or SIEM.",
    icon: Forward,
  },
  {
    id: "suppress",
    label: "Suppress",
    description: "Mute matching detections for a window.",
    icon: ShieldOff,
  },
  {
    id: "block",
    label: "Block indicator",
    description: "Push the indicator to the blocklist.",
    icon: Ban,
    destructive: true,
  },
];
