import type { LucideIcon } from "lucide-react";
import { motion } from "framer-motion";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface IntelCardProps {
  title: string;
  description?: string;
  icon?: LucideIcon;
  action?: ReactNode;
  badge?: ReactNode;
  children: ReactNode;
  className?: string;
  delay?: number;
}

/** Glass surface used by every Threat Intelligence widget. */
export function IntelCard({
  title,
  description,
  icon: Icon,
  action,
  badge,
  children,
  className,
  delay = 0,
}: IntelCardProps) {
  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-40px" }}
      transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1], delay }}
      className={cn(
        "glass-panel flex flex-col gap-4 p-5 transition-colors hover:border-primary/30",
        className,
      )}
    >
      <header className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          {Icon && (
            <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
              <Icon className="size-4" aria-hidden="true" />
            </span>
          )}
          <div className="space-y-0.5">
            <h2 className="text-sm font-semibold tracking-tight">{title}</h2>
            {description && <p className="text-xs text-muted-foreground">{description}</p>}
          </div>
        </div>
        {(badge ?? action) && (
          <div className="flex shrink-0 items-center gap-2">
            {badge}
            {action}
          </div>
        )}
      </header>
      <div className="flex-1">{children}</div>
    </motion.section>
  );
}
