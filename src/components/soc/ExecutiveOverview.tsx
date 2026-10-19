import { motion } from "framer-motion";
import { ShieldCheck, TrendingUp, Radar } from "lucide-react";

import { Skeleton } from "@/components/common/Skeletons";
import { useDashboardSummary } from "@/hooks/useDashboard";
import { cn } from "@/lib/utils";
import { formatNumber } from "@/utils/format";

function scoreTone(score: number) {
  if (score >= 80) return { text: "text-success", ring: "stroke-success", label: "Low risk" };
  if (score >= 60) return { text: "text-warning", ring: "stroke-warning", label: "Elevated risk" };
  return { text: "text-destructive", ring: "stroke-destructive", label: "High risk" };
}

function ScoreGauge({ score }: { score: number }) {
  const tone = scoreTone(score);
  const radius = 52;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - Math.min(100, Math.max(0, score)) / 100);

  return (
    <div className="relative grid size-36 shrink-0 place-items-center">
      <svg viewBox="0 0 128 128" className="size-36 -rotate-90">
        <circle
          cx="64"
          cy="64"
          r={radius}
          className="fill-none stroke-border"
          strokeWidth="8"
          strokeLinecap="round"
        />
        <motion.circle
          cx="64"
          cy="64"
          r={radius}
          className={cn("fill-none", tone.ring)}
          strokeWidth="8"
          strokeLinecap="round"
          strokeDasharray={circumference}
          initial={{ strokeDashoffset: circumference }}
          animate={{ strokeDashoffset: offset }}
          transition={{ duration: 1, ease: [0.22, 1, 0.36, 1] }}
        />
      </svg>
      <div className="absolute text-center">
        <p className={cn("font-display text-4xl font-semibold tabular-nums", tone.text)}>{score}</p>
        <p className="text-[11px] tracking-wide text-muted-foreground uppercase">{tone.label}</p>
      </div>
    </div>
  );
}

function SeverityBar({
  label,
  count,
  total,
  className,
}: {
  label: string;
  count: number;
  total: number;
  className: string;
}) {
  const pct = total > 0 ? Math.round((count / total) * 100) : 0;
  return (
    <li>
      <div className="flex items-baseline justify-between gap-2 text-xs">
        <span className="font-medium">{label}</span>
        <span className="tabular-nums text-muted-foreground">
          {formatNumber(count)} · {pct}%
        </span>
      </div>
      <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-muted/60">
        <motion.div
          className={cn("h-full rounded-full", className)}
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
        />
      </div>
    </li>
  );
}

/** Executive security overview: posture score, investigations and severity mix. */
export function ExecutiveOverview() {
  const { data, isLoading } = useDashboardSummary();

  const total = data?.threatsDetected ?? 0;
  const critical = data?.criticalThreats ?? 0;
  const high = data?.highThreats ?? 0;
  const medium = Math.max(0, total - critical - high);

  return (
    <section className="glass-panel relative isolate overflow-hidden rounded-2xl p-6">
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -top-24 -right-10 -z-10 size-64 rounded-full bg-primary/10 blur-3xl"
      />
      <header className="mb-6 flex flex-wrap items-center gap-2">
        <ShieldCheck className="size-4 text-primary" aria-hidden="true" />
        <h2 className="text-sm font-semibold tracking-tight">Executive security overview</h2>
        <span className="ms-auto text-xs text-muted-foreground">Live from detection engine</span>
      </header>

      {isLoading ? (
        <div className="grid gap-6 lg:grid-cols-[auto_1fr_1fr]">
          <Skeleton className="size-36 rounded-full" />
          <Skeleton className="h-36 w-full" />
          <Skeleton className="h-36 w-full" />
        </div>
      ) : (
        <div className="grid gap-8 lg:grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)] lg:items-center">
          <div className="flex justify-center">
            <ScoreGauge score={data?.securityScore ?? 0} />
          </div>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
            <div className="rounded-xl border border-border/70 bg-surface/40 p-4">
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <Radar className="size-3.5" aria-hidden="true" />
                Active investigations
              </div>
              <p className="mt-1 font-display text-2xl font-semibold tabular-nums">
                {formatNumber(data?.activeInvestigations ?? 0)}
              </p>
              <p className="text-xs text-muted-foreground">
                {formatNumber(data?.runningAnalyses ?? 0)} inspection
                {(data?.runningAnalyses ?? 0) === 1 ? "" : "s"} running now
              </p>
            </div>
            <div className="rounded-xl border border-border/70 bg-surface/40 p-4">
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <TrendingUp className="size-3.5" aria-hidden="true" />
                Completed inspections
              </div>
              <p className="mt-1 font-display text-2xl font-semibold tabular-nums">
                {formatNumber(data?.completedAnalyses ?? 0)}
              </p>
              <p className="text-xs text-muted-foreground">
                of {formatNumber(data?.totalAnalyses ?? 0)} captures submitted
              </p>
            </div>
          </div>

          <div>
            <p className="mb-3 text-xs font-medium tracking-wide text-muted-foreground uppercase">
              Threat severity distribution
            </p>
            {total === 0 ? (
              <p className="rounded-xl border border-dashed border-border bg-muted/20 px-4 py-6 text-center text-sm text-muted-foreground">
                No threats detected yet
              </p>
            ) : (
              <ul className="space-y-3">
                <SeverityBar
                  label="Critical"
                  count={critical}
                  total={total}
                  className="bg-destructive"
                />
                <SeverityBar label="High" count={high} total={total} className="bg-primary" />
                <SeverityBar label="Medium" count={medium} total={total} className="bg-warning" />
              </ul>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
