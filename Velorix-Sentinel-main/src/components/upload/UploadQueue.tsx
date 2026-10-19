import { AnimatePresence, motion } from "framer-motion";
import { AlertTriangle, CheckCircle2, FileArchive, RotateCcw, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import type { UploadQueueItem } from "@/hooks/useUploadQueue";
import { formatBytes } from "@/utils/format";

interface UploadQueueProps {
  items: UploadQueueItem[];
  onRetry: (id: string) => void;
  onDismiss: (id: string) => void;
  onClearCompleted: () => void;
}

/** Live transfer list: per-file progress, success animation and retry on failure. */
export function UploadQueue({ items, onRetry, onDismiss, onClearCompleted }: UploadQueueProps) {
  if (items.length === 0) return null;

  const completed = items.filter((item) => item.state === "success").length;

  return (
    <section aria-label="Upload queue" className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold tracking-wide text-muted-foreground uppercase">
          Transfers ({items.length})
        </h2>
        {completed > 0 && (
          <Button variant="ghost" size="sm" onClick={onClearCompleted}>
            Clear completed
          </Button>
        )}
      </div>

      <ul className="space-y-2">
        <AnimatePresence initial={false}>
          {items.map((item) => (
            <motion.li
              key={item.id}
              layout
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, height: 0 }}
              className={cn(
                "flex items-center gap-4 rounded-xl border border-border bg-card px-4 py-3",
                item.state === "error" && "border-destructive/50",
                item.state === "success" && "border-success/40",
              )}
            >
              <span
                aria-hidden="true"
                className={cn(
                  "grid size-9 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground",
                  item.state === "success" && "bg-success/15 text-success",
                  item.state === "error" && "bg-destructive/15 text-destructive",
                )}
              >
                {item.state === "success" ? (
                  <motion.span
                    initial={{ scale: 0.4, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    transition={{ type: "spring", stiffness: 320, damping: 18 }}
                  >
                    <CheckCircle2 className="size-4" />
                  </motion.span>
                ) : item.state === "error" ? (
                  <AlertTriangle className="size-4" />
                ) : (
                  <FileArchive className="size-4" />
                )}
              </span>

              <div className="min-w-0 flex-1 space-y-1.5">
                <div className="flex items-baseline justify-between gap-3">
                  <p className="truncate text-sm font-medium">{item.file.name}</p>
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {formatBytes(item.file.size)}
                  </span>
                </div>

                {item.state === "uploading" || item.state === "pending" ? (
                  <>
                    <Progress value={item.progress} className="h-1.5" />
                    <p className="text-xs text-muted-foreground">
                      {item.state === "pending" ? "Queued" : `Uploading… ${item.progress}%`}
                    </p>
                  </>
                ) : item.state === "success" ? (
                  <p className="text-xs text-success">Stored and ready for analysis</p>
                ) : (
                  <p className="text-xs text-destructive">{item.error}</p>
                )}
              </div>

              <div className="flex shrink-0 items-center gap-1">
                {item.state === "error" && (
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Retry ${item.file.name}`}
                    onClick={() => onRetry(item.id)}
                  >
                    <RotateCcw className="size-4" />
                  </Button>
                )}
                {item.state !== "uploading" && (
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Dismiss ${item.file.name}`}
                    onClick={() => onDismiss(item.id)}
                  >
                    <X className="size-4" />
                  </Button>
                )}
              </div>
            </motion.li>
          ))}
        </AnimatePresence>
      </ul>
    </section>
  );
}
