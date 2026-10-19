import { Eraser, Pause, Play, TerminalSquare } from "lucide-react";
import { useMemo, useState } from "react";

import { AwaitingTelemetry } from "./AwaitingTelemetry";
import { Button } from "@/components/ui/button";
import { SOC_WS_TOPICS } from "@/services/socService";
import { useSocStream } from "@/hooks/useSocStream";
import { cn } from "@/lib/utils";
import type { ConsoleChannel, ConsoleLevel, ConsoleLogEntry } from "@/types/soc";

const CHANNELS: (ConsoleChannel | "ALL")[] = ["ALL", "SYSTEM", "AUDIT", "SECURITY", "PACKET"];

const LEVEL_TONE: Record<ConsoleLevel, string> = {
  INFO: "text-sky-400",
  WARN: "text-amber-400",
  ERROR: "text-primary",
  DEBUG: "text-muted-foreground",
};

/**
 * Terminal-styled activity console. Lines arrive on the console topic; the
 * gateway is not live yet, so no synthetic log traffic is printed.
 */
export function ActivityConsole({ className }: { className?: string }) {
  const [channel, setChannel] = useState<ConsoleChannel | "ALL">("ALL");
  const [paused, setPaused] = useState(false);
  const { events, disabled, clear } = useSocStream<ConsoleLogEntry>(SOC_WS_TOPICS.console, 300);

  const lines = useMemo(
    () => (channel === "ALL" ? events : events.filter((line) => line.channel === channel)),
    [events, channel],
  );

  return (
    <div className={cn("flex min-h-0 flex-col gap-2", className)}>
      <div className="flex flex-wrap items-center gap-1.5">
        {CHANNELS.map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => setChannel(item)}
            className={cn(
              "focus-ring rounded-full border px-2.5 py-1 text-[10px] tracking-wide uppercase transition-colors",
              channel === item
                ? "border-primary/40 bg-primary/15 text-primary"
                : "border-border text-muted-foreground hover:text-foreground",
            )}
          >
            {item}
          </button>
        ))}
        <div className="ms-auto flex items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            className="h-7 px-2 text-[11px]"
            disabled={disabled}
            onClick={() => setPaused((value) => !value)}
          >
            {paused ? (
              <Play className="size-3.5" aria-hidden="true" />
            ) : (
              <Pause className="size-3.5" aria-hidden="true" />
            )}
            {paused ? "Resume" : "Pause"}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="h-7 px-2 text-[11px]"
            disabled={disabled || lines.length === 0}
            onClick={clear}
          >
            <Eraser className="size-3.5" aria-hidden="true" />
            Clear
          </Button>
        </div>
      </div>

      {lines.length === 0 ? (
        <AwaitingTelemetry
          compact
          icon={TerminalSquare}
          title="Console idle — no log stream attached."
          detail="System, audit, security and packet-engine events print here once the SOC realtime gateway is connected."
        />
      ) : (
        <div
          role="log"
          aria-live="polite"
          className="min-h-0 flex-1 overflow-y-auto rounded-xl border border-border/70 bg-background/80 p-3 font-mono text-[11px] leading-relaxed"
        >
          {lines.map((line) => (
            <p key={line.id} className="flex gap-2">
              <span className="shrink-0 text-muted-foreground">
                {new Date(line.timestamp).toLocaleTimeString()}
              </span>
              <span className={cn("shrink-0 uppercase", LEVEL_TONE[line.level])}>{line.level}</span>
              <span className="shrink-0 text-muted-foreground">[{line.channel}]</span>
              <span className="min-w-0 break-words">{line.message}</span>
            </p>
          ))}
        </div>
      )}
    </div>
  );
}
