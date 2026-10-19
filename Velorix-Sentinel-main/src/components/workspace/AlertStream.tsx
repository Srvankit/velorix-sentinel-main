import { Activity, Pause, Play, RadioTower } from "lucide-react";
import { useMemo, useState } from "react";

import { AlertSeverityChip, AlertStatusChip, RiskScore } from "./AlertChips";
import { AwaitingTelemetry } from "./AwaitingTelemetry";
import { Button } from "@/components/ui/button";
import { SOC_WS_TOPICS } from "@/services/socService";
import { useSocStream } from "@/hooks/useSocStream";
import { cn } from "@/lib/utils";
import type { AlertSeverity, SocAlert } from "@/types/soc";

const SEVERITIES: AlertSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFORMATIONAL"];

interface AlertStreamProps {
  className?: string;
  onSelect?: (alert: SocAlert) => void;
  selectedId?: string | null;
}

/**
 * Live alert stream.
 *
 * Subscribes to the SOC alert topic through `useSocStream`. The realtime
 * gateway is not deployed yet, so the stream stays empty and the panel says so
 * rather than replaying fabricated detections.
 */
export function AlertStream({ className, onSelect, selectedId }: AlertStreamProps) {
  const [paused, setPaused] = useState(false);
  const [filter, setFilter] = useState<AlertSeverity | "ALL">("ALL");
  const { events, disabled } = useSocStream<SocAlert>(SOC_WS_TOPICS.alerts);

  const visible = useMemo(
    () => (filter === "ALL" ? events : events.filter((alert) => alert.severity === filter)),
    [events, filter],
  );

  return (
    <div className={cn("flex min-h-0 flex-col gap-3", className)}>
      <div className="flex flex-wrap items-center gap-1.5">
        <FilterPill active={filter === "ALL"} onClick={() => setFilter("ALL")} label="All" />
        {SEVERITIES.map((severity) => (
          <FilterPill
            key={severity}
            active={filter === severity}
            onClick={() => setFilter(severity)}
            label={severity.charAt(0) + severity.slice(1).toLowerCase()}
          />
        ))}
        <Button
          variant="ghost"
          size="sm"
          className="ms-auto h-7 px-2 text-[11px]"
          onClick={() => setPaused((value) => !value)}
          disabled={disabled}
          title={disabled ? "Stream connects with the realtime gateway." : undefined}
        >
          {paused ? <Play className="size-3.5" /> : <Pause className="size-3.5" />}
          {paused ? "Resume" : "Pause"}
        </Button>
      </div>

      {visible.length === 0 ? (
        <AwaitingTelemetry
          icon={RadioTower}
          title="No live alerts available."
          detail="The alert stream attaches to the SOC realtime channel. Detections appear the moment the gateway and threat engine start publishing."
        />
      ) : (
        <ul className="min-h-0 flex-1 space-y-2 overflow-y-auto">
          {visible.map((alert) => (
            <li key={alert.id}>
              <button
                type="button"
                onClick={() => onSelect?.(alert)}
                aria-current={selectedId === alert.id ? "true" : undefined}
                className={cn(
                  "focus-ring w-full rounded-xl border p-3 text-left transition-colors",
                  selectedId === alert.id
                    ? "border-primary/40 bg-primary/10"
                    : "border-border/70 bg-card/40 hover:border-primary/30",
                )}
              >
                <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-2">
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-medium">{alert.name}</span>
                    <span className="block font-mono text-[11px] text-muted-foreground">
                      {new Date(alert.observedAt ?? alert.createdAt).toLocaleTimeString()} ·{" "}
                      {alert.reference}
                    </span>
                  </span>
                  <span className="flex shrink-0 items-center gap-1.5">
                    <AlertSeverityChip severity={alert.severity} />
                    <RiskScore score={alert.riskScore} />
                  </span>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-muted-foreground">
                  <span>
                    {alert.sourceAddress ?? "—"}
                    {alert.sourcePort ? `:${alert.sourcePort}` : ""}
                  </span>
                  <span aria-hidden="true">→</span>
                  <span>
                    {alert.destinationAddress ?? "—"}
                    {alert.destinationPort ? `:${alert.destinationPort}` : ""}
                  </span>
                  <span className="rounded border border-border px-1">{alert.protocol ?? "—"}</span>
                  <AlertStatusChip status={alert.status} />
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}

      <p className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
        <Activity className="size-3" aria-hidden="true" />
        Channel <code className="font-mono">{SOC_WS_TOPICS.alerts}</code> ·{" "}
        {disabled ? "gateway not deployed" : paused ? "paused" : "listening"}
      </p>
    </div>
  );
}

function FilterPill({
  active,
  onClick,
  label,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "focus-ring rounded-full border px-2.5 py-1 text-[11px] transition-colors",
        active
          ? "border-primary/40 bg-primary/15 text-primary"
          : "border-border text-muted-foreground hover:border-primary/30 hover:text-foreground",
      )}
    >
      {label}
    </button>
  );
}
