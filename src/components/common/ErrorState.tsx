import { AlertTriangle, RotateCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { describeError } from "@/lib/errors";

interface ErrorStateProps {
  /** Overrides the classified title when provided. */
  title?: string;
  error?: unknown;
  onRetry?: () => void;
  className?: string;
}

/** Canonical inline error state, driven by the centralised error classifier. */
export function ErrorState({ title, error, onRetry, className }: ErrorStateProps) {
  const described = describeError(error);

  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 px-6 py-12 text-center",
        className,
      )}
    >
      <span className="grid size-12 place-items-center rounded-xl bg-destructive/15 text-destructive">
        <AlertTriangle className="size-5" aria-hidden="true" />
      </span>
      <h3 className="text-base font-semibold">{title ?? described.title}</h3>
      <p className="max-w-md text-sm text-muted-foreground">{described.message}</p>
      {onRetry && described.retryable && (
        <Button variant="outline" size="sm" onClick={onRetry} className="mt-1">
          <RotateCw className="size-4" aria-hidden="true" />
          Try again
        </Button>
      )}
    </div>
  );
}
