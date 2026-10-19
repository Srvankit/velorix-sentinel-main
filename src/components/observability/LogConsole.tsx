import { Download, Search } from "lucide-react";
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import type { LogEntry, LogSeverity } from "@/types/observability";

import { AwaitingMonitoring } from "./AwaitingMonitoring";
import { SeverityTag } from "./OpsBadges";

const SEVERITIES: LogSeverity[] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"];

const MESSAGE_TONE: Record<LogSeverity, string> = {
  TRACE: "text-muted-foreground",
  DEBUG: "text-muted-foreground",
  INFO: "text-foreground/90",
  WARN: "text-amber-300",
  ERROR: "text-primary",
  FATAL: "text-primary",
};

interface LogConsoleProps {
  entries: LogEntry[];
  isLoading?: boolean;
  awaiting?: boolean;
  channelLabel: string;
  className?: string;
}

/**
 * Terminal-styled log viewer with search, severity filtering and an export
 * placeholder. Renders no synthetic log lines.
 */
export function LogConsole({
  entries,
  isLoading = false,
  awaiting = true,
  channelLabel,
  className,
}: LogConsoleProps) {
  const [search, setSearch] = useState("");
  const [active, setActive] = useState<LogSeverity[]>([]);

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return entries.filter((entry) => {
      if (active.length && !active.includes(entry.severity)) return false;
      if (!needle) return true;
      return `${entry.logger} ${entry.message} ${entry.traceId ?? ""} ${entry.actor ?? ""}`
        .toLowerCase()
        .includes(needle);
    });
  }, [entries, search, active]);

  function toggle(severity: LogSeverity) {
    setActive((current) =>
      current.includes(severity)
        ? current.filter((item) => item !== severity)
        : [...current, severity],
    );
  }

  return (
    <div className={cn("space-y-3", className)}>
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-[200px] flex-1">
          <Search
            className="pointer-events-none absolute top-1/2 left-3 size-3.5 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={`Search ${channelLabel.toLowerCase()}…`}
            aria-label={`Search ${channelLabel}`}
            className="h-9 pl-9 text-xs"
          />
        </div>

        <div
          className="flex flex-wrap items-center gap-1"
          role="group"
          aria-label="Severity filter"
        >
          {SEVERITIES.map((severity) => (
            <button
              key={severity}
              type="button"
              onClick={() => toggle(severity)}
              aria-pressed={active.includes(severity)}
              className={cn(
                "rounded-md border px-2 py-1 font-mono text-[10px] font-semibold transition-colors",
                active.includes(severity)
                  ? "border-primary/40 bg-primary/10 text-primary"
                  : "border-border bg-muted/20 text-muted-foreground hover:text-foreground",
              )}
            >
              {severity}
            </button>
          ))}
        </div>

        <Button variant="outline" size="sm" disabled className="h-9 gap-1.5 text-xs">
          <Download className="size-3.5" aria-hidden="true" />
          Export
        </Button>
      </div>

      <div className="overflow-hidden rounded-2xl border border-border/70 bg-[oklch(0.16_0.01_260)]/60 backdrop-blur-xl">
        <div className="flex items-center justify-between border-b border-border/60 px-3 py-2">
          <span className="font-mono text-[11px] text-muted-foreground">
            velorix@sentinel:~$ tail -f {channelLabel.toLowerCase().replace(/\s+/g, "-")}.log
          </span>
          <span className="font-mono text-[10px] text-muted-foreground">
            {awaiting ? "stream offline" : `${filtered.length} lines`}
          </span>
        </div>

        <div className="max-h-[420px] overflow-auto p-3">
          {awaiting || (!isLoading && filtered.length === 0) ? (
            <AwaitingMonitoring
              title="Awaiting Live Platform Monitoring"
              detail={`${channelLabel} stream from the log pipeline (Loki / Elastic) once log shipping is enabled. No sample log lines are generated.`}
              className="border-none bg-transparent"
            />
          ) : (
            <ul className="space-y-1">
              {filtered.map((entry) => (
                <li
                  key={entry.id}
                  className="grid grid-cols-[auto_auto_minmax(0,1fr)] items-start gap-2 rounded px-1 py-0.5 font-mono text-[11px] hover:bg-muted/20"
                >
                  <span className="text-muted-foreground">
                    {new Date(entry.timestamp).toISOString()}
                  </span>
                  <SeverityTag severity={entry.severity} />
                  <span className={cn("break-words", MESSAGE_TONE[entry.severity])}>
                    <span className="text-sky-400">{entry.logger}</span>{" "}
                    <span className="text-muted-foreground">—</span> {entry.message}
                    {entry.traceId && (
                      <span className="ml-2 text-muted-foreground">trace={entry.traceId}</span>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
