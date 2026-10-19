import { BotMessageSquare, ShieldQuestion, Sparkles } from "lucide-react";

import { cn } from "@/lib/utils";

/** Premium onboarding canvas shown before the first question is asked. */
export function CopilotEmptyState({ className }: { className?: string }) {
  return (
    <div className={cn("relative overflow-hidden rounded-2xl px-6 py-12 text-center", className)}>
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-70 [background:radial-gradient(60%_60%_at_50%_0%,color-mix(in_oklab,var(--primary)_18%,transparent),transparent_70%)]"
      />
      <div className="relative mx-auto flex max-w-lg flex-col items-center gap-3">
        <span className="relative grid size-16 place-items-center rounded-2xl border border-primary/30 bg-gradient-brand text-primary-foreground shadow-lg">
          <BotMessageSquare className="size-7" aria-hidden="true" />
          <span className="absolute -right-1 -bottom-1 grid size-6 place-items-center rounded-lg border border-border bg-background text-primary">
            <Sparkles className="size-3" aria-hidden="true" />
          </span>
        </span>

        <h2 className="font-display text-xl font-semibold tracking-tight">
          No AI conversation yet
        </h2>
        <p className="text-sm text-muted-foreground">
          Start asking security questions. The copilot is designed to explain detections, summarise
          investigations and draft SOC reporting once the inference backend is connected.
        </p>

        <p className="mt-2 inline-flex items-center gap-2 rounded-full border border-border px-3 py-1 text-[11px] text-muted-foreground">
          <ShieldQuestion className="size-3" aria-hidden="true" />
          Questions are stored locally in this browser only
        </p>
      </div>
    </div>
  );
}
