import { Zap } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { QUICK_ACTIONS, type QuickAction } from "./quickActions.data";

interface QuickActionsProps {
  disabled?: boolean;
  onAction?: (action: QuickAction) => void;
  className?: string;
}

export function QuickActions({ disabled = true, onAction, className }: QuickActionsProps) {
  return (
    <div className={cn("space-y-2", className)}>
      <p className="flex items-center gap-1.5 text-[10px] tracking-wide text-muted-foreground uppercase">
        <Zap className="size-3" aria-hidden="true" />
        Response actions
      </p>
      <div className="grid gap-1.5">
        {QUICK_ACTIONS.map((action) => (
          <Button
            key={action.id}
            variant="outline"
            size="sm"
            disabled={disabled}
            onClick={() => onAction?.(action)}
            title={
              disabled
                ? "Available once an alert is selected and the SOC action API is live."
                : action.description
            }
            className={cn(
              "h-auto justify-start gap-2 px-3 py-2 text-left",
              action.destructive && "border-primary/30 text-primary hover:bg-primary/10",
            )}
          >
            <action.icon className="size-3.5 shrink-0" aria-hidden="true" />
            <span className="min-w-0">
              <span className="block truncate text-xs font-medium">{action.label}</span>
              <span className="block truncate text-[10px] font-normal text-muted-foreground">
                {action.description}
              </span>
            </span>
          </Button>
        ))}
      </div>
    </div>
  );
}
