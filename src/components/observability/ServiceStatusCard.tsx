import { motion } from "framer-motion";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import type { ServiceHealth } from "@/types/observability";

import { HealthBadge } from "./OpsBadges";

interface ServiceStatusCardProps {
  service: ServiceHealth;
  icon: LucideIcon;
  delay?: number;
  className?: string;
}

/** Operations Dashboard tile for a single monitored platform component. */
export function ServiceStatusCard({
  service,
  icon: Icon,
  delay = 0,
  className,
}: ServiceStatusCardProps) {
  const rows: { label: string; value: string }[] = [
    {
      label: "Uptime",
      value: service.uptime != null ? `${service.uptime.toFixed(2)}%` : "Awaiting Live Monitoring",
    },
    {
      label: "Latency",
      value: service.latencyMs != null ? `${service.latencyMs} ms` : "Awaiting Live Monitoring",
    },
    {
      label: "Last check",
      value: service.lastCheckedAt
        ? new Date(service.lastCheckedAt).toLocaleString()
        : "Awaiting Live Monitoring",
    },
  ];

  return (
    <motion.article
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1], delay }}
      className={cn(
        "group relative overflow-hidden rounded-2xl border border-border/70 bg-card/40 p-4 backdrop-blur-xl transition-colors hover:border-primary/30",
        className,
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
            <Icon className="size-4" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <h3 className="truncate text-sm font-semibold">{service.name}</h3>
            <p className="mt-0.5 text-[11px] text-muted-foreground">{service.description}</p>
          </div>
        </div>
        <HealthBadge state={service.state} />
      </div>

      <dl className="mt-3 space-y-1.5 border-t border-border/60 pt-3">
        {rows.map((row) => (
          <div key={row.label} className="flex items-center justify-between gap-3">
            <dt className="text-[11px] text-muted-foreground">{row.label}</dt>
            <dd className="truncate font-mono text-[11px]">{row.value}</dd>
          </div>
        ))}
      </dl>
    </motion.article>
  );
}
