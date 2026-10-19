import { useNavigate } from "@tanstack/react-router";
import {
  Bell,
  BookOpen,
  BotMessageSquare,
  Crosshair,
  LayoutDashboard,
  Radar,
  Siren,
  TerminalSquare,
  UploadCloud,
} from "lucide-react";

import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "@/components/ui/command";

interface WorkspaceCommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onOpenNotifications?: () => void;
  onFocusTriage?: () => void;
}

const NAVIGATION = [
  { label: "SOC Workspace", to: "/soc", icon: TerminalSquare },
  { label: "Dashboard", to: "/dashboard", icon: LayoutDashboard },
  { label: "Incidents", to: "/incidents", icon: Siren },
  { label: "Threat Intelligence", to: "/intel", icon: Crosshair },
  { label: "Analysis", to: "/analysis", icon: Radar },
  { label: "Upload capture", to: "/upload", icon: UploadCloud },
  { label: "AI Copilot", to: "/assistant", icon: BotMessageSquare },
  { label: "Knowledge Center", to: "/knowledge", icon: BookOpen },
] as const;

/** Ctrl/⌘+K command palette scoped to the SOC workspace. */
export function WorkspaceCommandPalette({
  open,
  onOpenChange,
  onOpenNotifications,
  onFocusTriage,
}: WorkspaceCommandPaletteProps) {
  const navigate = useNavigate();

  const run = (action: () => void) => {
    onOpenChange(false);
    action();
  };

  return (
    <CommandDialog open={open} onOpenChange={onOpenChange}>
      <CommandInput placeholder="Search commands, modules and workspace actions…" />
      <CommandList>
        <CommandEmpty>No matching command.</CommandEmpty>
        <CommandGroup heading="Navigate">
          {NAVIGATION.map((item) => (
            <CommandItem
              key={item.to}
              value={item.label}
              onSelect={() => run(() => void navigate({ to: item.to }))}
            >
              <item.icon className="size-4" aria-hidden="true" />
              {item.label}
            </CommandItem>
          ))}
        </CommandGroup>
        <CommandSeparator />
        <CommandGroup heading="Workspace">
          <CommandItem value="Focus triage queue" onSelect={() => run(() => onFocusTriage?.())}>
            <Radar className="size-4" aria-hidden="true" />
            Focus triage queue
          </CommandItem>
          <CommandItem
            value="Open notifications"
            onSelect={() => run(() => onOpenNotifications?.())}
          >
            <Bell className="size-4" aria-hidden="true" />
            Open notification center
          </CommandItem>
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  );
}
