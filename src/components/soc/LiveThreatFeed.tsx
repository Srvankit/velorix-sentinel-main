import { Link } from "@tanstack/react-router";
import { ArrowUpRight, ShieldAlert, ShieldCheck } from "lucide-react";

import { EmptyState } from "@/components/common/EmptyState";
import { SeverityBadge } from "@/components/common/SeverityBadge";
import { SkeletonList } from "@/components/common/Skeletons";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/button";
import { useRecentThreats } from "@/hooks/useDashboard";
import { cn } from "@/lib/utils";
import { formatRelativeTime } from "@/utils/format";

/**
 * Live threat feed. Renders real detections when the engine has produced them
 * and a premium "No threats detected" state otherwise — never mocked alerts.
 */
export function LiveThreatFeed({ limit = 8 }: { limit?: number }) {
  const { data, isLoading, isError, error } = useRecentThreats(limit);
  const threats = data ?? [];

  return (
    <section className="glass-panel rounded-2xl p-5">
      <header className="mb-4 grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="relative flex size-2">
              <span
                className={cn(
                  "absolute inline-flex size-full rounded-full opacity-75",
                  threats.length > 0 ? "animate-ping bg-destructive" : "bg-success",
                )}
              />
              <span
                className={cn(
                  "relative inline-flex size-2 rounded-full",
                  threats.length > 0 ? "bg-destructive" : "bg-success",
                )}
              />
            </span>
            <h2 className="truncate text-sm font-semibold tracking-tight">Live threat feed</h2>
          </div>
          <p className="text-xs text-muted-foreground">Streaming detections from the rule engine</p>
        </div>
        <Button variant="ghost" size="sm" asChild>
          <Link to="/threats">
            All threats
            <ArrowUpRight className="size-3.5" aria-hidden="true" />
          </Link>
        </Button>
      </header>

      {isLoading ? (
        <SkeletonList items={4} />
      ) : isError ? (
        <p className="text-sm text-muted-foreground">{error?.message}</p>
      ) : threats.length === 0 ? (
        <EmptyState
          icon={ShieldCheck}
          title="No threats detected"
          description="The detection engine has not raised any findings. New alerts appear here the moment a rule matches."
          className="py-10"
        />
      ) : (
        <ul className="space-y-2">
          {threats.map((threat) => (
            <li
              key={threat.id}
              className="group rounded-xl border border-border/70 bg-surface/40 p-3 transition-colors hover:border-primary/30 hover:bg-surface/70"
            >
              <div className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-start gap-3">
                <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-destructive/12 text-destructive">
                  <ShieldAlert className="size-4" aria-hidden="true" />
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{threat.title}</p>
                  <p className="mt-0.5 truncate font-mono text-xs text-muted-foreground">
                    {threat.sourceIp ?? "unknown"} → {threat.destinationIp ?? "unknown"} ·{" "}
                    {threat.protocol} · {threat.threatType.replace(/_/g, " ").toLowerCase()}
                  </p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1.5">
                  <SeverityBadge severity={threat.severity} />
                  <span className="text-[11px] text-muted-foreground">
                    {formatRelativeTime(threat.detectedAt)}
                  </span>
                </div>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2 ps-11">
                <StatusBadge status={threat.status} />
                <span className="text-[11px] text-muted-foreground">
                  {threat.confidencePercent}% confidence
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
