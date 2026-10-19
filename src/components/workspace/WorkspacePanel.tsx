import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

interface WorkspacePanelProps {
  title: string;
  description?: string;
  icon?: LucideIcon;
  actions?: ReactNode;
  badge?: ReactNode;
  children: ReactNode;
  /** Panel body scrolls independently — used inside resizable regions. */
  scroll?: boolean;
  className?: string;
  bodyClassName?: string;
}

/** Glass panel chrome shared by every SOC workspace region. */
export function WorkspacePanel({
  title,
  description,
  icon: Icon,
  actions,
  badge,
  children,
  scroll = false,
  className,
  bodyClassName,
}: WorkspacePanelProps) {
  return (
    <section
      className={cn(
        "flex min-h-0 min-w-0 flex-col rounded-2xl border border-border/70 bg-card/40 backdrop-blur-xl",
        className,
      )}
    >
      <header className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 border-b border-border/60 px-4 py-2.5">
        <div className="flex min-w-0 items-center gap-2.5">
          {Icon && (
            <span className="grid size-7 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
              <Icon className="size-3.5" aria-hidden="true" />
            </span>
          )}
          <div className="min-w-0">
            <h2 className="truncate text-xs font-semibold tracking-wide uppercase">{title}</h2>
            {description && (
              <p className="truncate text-[11px] text-muted-foreground">{description}</p>
            )}
          </div>
          {badge}
        </div>
        {actions && <div className="flex shrink-0 items-center gap-1.5">{actions}</div>}
      </header>
      <div className={cn("min-h-0 flex-1 p-3", scroll && "overflow-y-auto", bodyClassName)}>
        {children}
      </div>
    </section>
  );
}
