import { ServerCrash } from "lucide-react";

import { apiConfig } from "@/utils/apiConfig";
import { useHydrated } from "@/hooks/useHydrated";

/**
 * Global, non-blocking banner shown when the configured API base URL cannot work
 * in the current environment (missing, invalid, or a localhost API behind a
 * remotely served frontend). Rendered only after hydration because the diagnosis
 * depends on the browser's location.
 */
export function ApiConfigBanner() {
  const hydrated = useHydrated();
  const problem = apiConfig.problem;

  if (!hydrated || !problem) return null;

  return (
    <div
      role="status"
      className="sticky top-0 z-[60] border-b border-destructive/40 bg-destructive/10 px-4 py-3 backdrop-blur"
    >
      <div className="mx-auto flex max-w-5xl items-start gap-3 text-left">
        <ServerCrash className="mt-0.5 size-4 shrink-0 text-destructive" aria-hidden="true" />
        <div className="space-y-0.5">
          <p className="text-sm font-semibold text-destructive">{problem.title}</p>
          <p className="text-xs leading-relaxed text-muted-foreground">{problem.message}</p>
        </div>
      </div>
    </div>
  );
}
