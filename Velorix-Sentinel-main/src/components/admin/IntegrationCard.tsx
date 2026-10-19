import type { LucideIcon } from "lucide-react";

import { IntegrationStateBadge } from "./AdminBadges";
import { cn } from "@/lib/utils";
import type { Integration } from "@/types/admin";

interface IntegrationCardProps {
  integration: Integration;
  icon: LucideIcon;
}

/** Premium integration tile. Connection state is never faked. */
export function IntegrationCard({ integration, icon: Icon }: IntegrationCardProps) {
  const dimmed = integration.state === "COMING_SOON";

  return (
    <article
      className={cn(
        "group relative overflow-hidden rounded-2xl border border-border/70 bg-card/40 p-4 backdrop-blur-xl transition-colors hover:border-primary/30",
        dimmed && "opacity-90",
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
          <Icon className="size-4" aria-hidden="true" />
        </span>
        <IntegrationStateBadge state={integration.state} />
      </div>
      <h3 className="mt-3 text-sm font-semibold">{integration.name}</h3>
      <p className="mt-1 text-xs text-muted-foreground">{integration.description}</p>
      <p className="mt-3 text-[10px] tracking-wide text-muted-foreground/80 uppercase">
        {integration.category.replace("_", " ")}
      </p>
    </article>
  );
}
