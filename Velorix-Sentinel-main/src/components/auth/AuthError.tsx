import { AlertCircle } from "lucide-react";

import { cn } from "@/lib/utils";
import { describeError } from "@/lib/errors";

/** Inline form-level error banner used by every auth form. */
export function AuthError({ error, className }: { error?: unknown; className?: string }) {
  if (!error) return null;
  const described = describeError(error);

  return (
    <div
      role="alert"
      className={cn(
        "flex items-start gap-2.5 rounded-xl border border-destructive/40 bg-destructive/10 px-3.5 py-3 text-sm text-destructive",
        className,
      )}
    >
      <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>
        <span className="font-medium">{described.title}.</span> {described.message}
      </span>
    </div>
  );
}
