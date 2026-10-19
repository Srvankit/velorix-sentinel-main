import { Loader2 } from "lucide-react";

import { cn } from "@/lib/utils";
import { APP_NAME } from "@/utils/constants";

interface SpinnerProps {
  className?: string;
  label?: string;
}

export function Spinner({ className, label = "Loading" }: SpinnerProps) {
  return (
    <span role="status" aria-live="polite" className="inline-flex items-center gap-2">
      <Loader2 className={cn("size-4 animate-spin text-primary", className)} aria-hidden="true" />
      <span className="sr-only">{label}</span>
    </span>
  );
}

/** Full-height loading state for route-level suspense and auth bootstrapping. */
export function PageLoader({ label = `Loading ${APP_NAME}` }: { label?: string }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3">
      <Spinner className="size-6" label={label} />
      <p className="text-sm text-muted-foreground">{label}…</p>
    </div>
  );
}
