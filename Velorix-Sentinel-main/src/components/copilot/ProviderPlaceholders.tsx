import { Cpu, Cloud, Server, Sparkle, Star } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

interface ProviderCard {
  name: string;
  description: string;
  icon: LucideIcon;
}

const PROVIDERS: ProviderCard[] = [
  { name: "OpenAI", description: "GPT reasoning for triage and reporting", icon: Sparkle },
  { name: "Azure OpenAI", description: "Enterprise tenancy with regional residency", icon: Cloud },
  { name: "Anthropic Claude", description: "Long-context capture and log analysis", icon: Star },
  { name: "Google Gemini", description: "Multimodal analysis of artefacts", icon: Sparkle },
  { name: "Local LLM", description: "Air-gapped inference inside your perimeter", icon: Server },
];

/** Upcoming inference backends. All marked Coming Soon — none are wired. */
export function ProviderPlaceholders({ className }: { className?: string }) {
  return (
    <section className={cn("glass-panel rounded-2xl p-5", className)}>
      <header className="mb-4 flex items-center gap-2">
        <Cpu className="size-4 text-primary" aria-hidden="true" />
        <div className="min-w-0">
          <h2 className="text-sm font-semibold tracking-tight">Inference providers</h2>
          <p className="text-xs text-muted-foreground">Planned integrations</p>
        </div>
      </header>

      <ul className="space-y-2">
        {PROVIDERS.map((provider) => (
          <li
            key={provider.name}
            className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 rounded-xl border border-border bg-surface/60 p-3"
          >
            <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
              <provider.icon className="size-4" aria-hidden="true" />
            </span>
            <span className="min-w-0">
              <span className="block truncate text-sm font-medium">{provider.name}</span>
              <span className="block truncate text-xs text-muted-foreground">
                {provider.description}
              </span>
            </span>
            <span className="shrink-0 rounded-full border border-info/40 bg-info/10 px-2 py-0.5 text-[10px] font-medium tracking-wider text-info uppercase">
              Coming soon
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
