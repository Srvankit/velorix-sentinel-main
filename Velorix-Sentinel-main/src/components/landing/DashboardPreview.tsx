import { motion } from "framer-motion";
import { Activity, Bot, Globe2, ShieldAlert, TrendingUp } from "lucide-react";

import { Skeleton, SkeletonList, SkeletonText } from "@/components/common";
import { cn } from "@/lib/utils";

/**
 * Console preview shown beside the hero copy.
 *
 * Deliberately contains no data: every surface renders a loading placeholder
 * until the backend is connected, so nothing on screen is fabricated.
 */

function Panel({ className, children }: { className?: string; children: React.ReactNode }) {
  return <div className={cn("glass-panel p-4", className)}>{children}</div>;
}

function PanelTitle({
  icon: Icon,
  children,
}: {
  icon: typeof Activity;
  children: React.ReactNode;
}) {
  return (
    <div className="mb-3 flex items-center gap-2 text-xs font-medium">
      <Icon className="size-4 text-primary" aria-hidden="true" />
      {children}
    </div>
  );
}

function RingPlaceholder({ label }: { label: string }) {
  const circumference = 2 * Math.PI * 26;
  return (
    <div className="flex flex-col items-center gap-2">
      <div className="relative size-[68px]">
        <svg viewBox="0 0 64 64" className="size-full -rotate-90" aria-hidden="true">
          <circle cx="32" cy="32" r="26" fill="none" strokeWidth="6" className="stroke-border" />
          <motion.circle
            cx="32"
            cy="32"
            r="26"
            fill="none"
            strokeWidth="6"
            strokeLinecap="round"
            className="stroke-primary/40"
            strokeDasharray={`${circumference * 0.22} ${circumference}`}
            animate={{ rotate: 360 }}
            style={{ originX: "50%", originY: "50%" }}
            transition={{ duration: 2.4, repeat: Infinity, ease: "linear" }}
          />
        </svg>
        <span className="absolute inset-0 grid place-items-center font-mono text-xs text-muted-foreground">
          —
        </span>
      </div>
      <span className="text-[11px] uppercase tracking-wider text-muted-foreground">{label}</span>
    </div>
  );
}

export function DashboardPreview() {
  return (
    <motion.div
      initial={{ opacity: 0, y: 30, rotateX: 6 }}
      animate={{ opacity: 1, y: 0, rotateX: 0 }}
      transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1], delay: 0.2 }}
      className="glass-panel relative w-full p-4 sm:p-5"
      aria-label="Velorix Sentinel console preview — awaiting live data"
    >
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="size-2.5 rounded-full bg-destructive/70" />
          <span className="size-2.5 rounded-full bg-warning/70" />
          <span className="size-2.5 rounded-full bg-success/70" />
        </div>
        <span className="font-mono text-[11px] text-muted-foreground">
          velorix://sentinel-console
        </span>
      </div>

      <div className="grid gap-3 sm:grid-cols-5">
        <Panel className="sm:col-span-3">
          <PanelTitle icon={ShieldAlert}>Active Threats</PanelTitle>
          <SkeletonList items={3} label="Threat feed connects once a sensor is attached" />
        </Panel>

        <Panel className="sm:col-span-2 flex flex-col items-center justify-center gap-3">
          <div className="flex gap-4">
            <RingPlaceholder label="Risk" />
            <RingPlaceholder label="Posture" />
          </div>
        </Panel>

        <Panel className="sm:col-span-3">
          <PanelTitle icon={Activity}>Traffic Throughput</PanelTitle>
          <div className="flex h-24 items-end gap-1.5">
            {Array.from({ length: 12 }).map((_, i) => (
              <motion.span
                key={i}
                className="flex-1 rounded-t bg-foreground/8"
                animate={{ height: ["24%", "58%", "32%"] }}
                transition={{
                  duration: 3.2,
                  delay: i * 0.08,
                  repeat: Infinity,
                  repeatType: "mirror",
                  ease: "easeInOut",
                }}
              />
            ))}
          </div>
        </Panel>

        <Panel className="sm:col-span-2">
          <PanelTitle icon={Globe2}>Attack Origins</PanelTitle>
          <div className="relative h-24 overflow-hidden rounded-lg border border-border bg-elevated/50">
            <div
              aria-hidden="true"
              className="absolute inset-0 opacity-40"
              style={{
                backgroundImage:
                  "linear-gradient(var(--color-border) 1px, transparent 1px), linear-gradient(90deg, var(--color-border) 1px, transparent 1px)",
                backgroundSize: "16px 16px",
              }}
            />
            <span className="absolute inset-0 grid place-items-center text-[11px] text-muted-foreground">
              Awaiting telemetry
            </span>
          </div>
        </Panel>

        <Panel className="sm:col-span-3">
          <PanelTitle icon={Bot}>AI Assistant</PanelTitle>
          <div className="rounded-lg border border-border bg-elevated/60 p-3">
            <SkeletonText lines={2} />
          </div>
        </Panel>

        <Panel className="sm:col-span-2">
          <PanelTitle icon={TrendingUp}>Live Alerts</PanelTitle>
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="flex items-center gap-2">
                <span className="size-1.5 shrink-0 rounded-full bg-border" />
                <Skeleton className="h-2.5 flex-1" />
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </motion.div>
  );
}
