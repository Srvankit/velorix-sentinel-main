import { AtSign, Eye, MessageSquare, Send, Users } from "lucide-react";
import { useState } from "react";

import { AwaitingBackend } from "./AwaitingBackend";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { AnalystRef, IncidentComment } from "@/types/incident";

interface CollaborationPanelProps {
  comments: IncidentComment[];
  watchers: AnalystRef[];
  /** Wired to POST /v1/incidents/{id}/comments once the backend ships. */
  onSubmit?: (body: string, internal: boolean) => void;
  disabled?: boolean;
  className?: string;
}

/** Discussion thread, internal notes, mentions and watcher list for a case. */
export function CollaborationPanel({
  comments,
  watchers,
  onSubmit,
  disabled = true,
  className,
}: CollaborationPanelProps) {
  const [draft, setDraft] = useState("");
  const [internal, setInternal] = useState(true);

  return (
    <div className={cn("grid gap-4 lg:grid-cols-[1fr_240px]", className)}>
      <div className="space-y-3">
        {comments.length === 0 ? (
          <AwaitingBackend
            icon={MessageSquare}
            title="No discussion yet."
            detail="Analyst notes, mentions and team discussion for this case appear here once the collaboration backend is connected."
          />
        ) : (
          <ul className="space-y-3">
            {comments.map((comment) => (
              <li
                key={comment.id}
                className="rounded-xl border border-border/70 bg-card/40 p-3 transition-colors hover:border-primary/30"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium">
                    {comment.author?.displayName ?? "System"}
                  </span>
                  <span className="flex items-center gap-2">
                    {comment.internal && (
                      <span className="rounded-full border border-amber-500/40 bg-amber-500/10 px-2 py-0.5 text-[10px] font-medium text-amber-400">
                        Internal
                      </span>
                    )}
                    <time className="font-mono text-[11px] text-muted-foreground">
                      {new Date(comment.createdAt).toLocaleString()}
                    </time>
                  </span>
                </div>
                <p className="mt-1.5 text-sm whitespace-pre-wrap text-muted-foreground">
                  {comment.body}
                </p>
                {comment.mentions.length > 0 && (
                  <p className="mt-1.5 flex flex-wrap gap-1.5">
                    {comment.mentions.map((mention) => (
                      <span
                        key={mention}
                        className="inline-flex items-center gap-0.5 rounded-full border border-primary/30 bg-primary/10 px-2 py-0.5 text-[10px] text-primary"
                      >
                        <AtSign className="size-2.5" aria-hidden="true" />
                        {mention}
                      </span>
                    ))}
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}

        <div className="rounded-xl border border-border/70 bg-card/40 p-3">
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            disabled={disabled}
            rows={3}
            placeholder={
              disabled
                ? "Commenting is available after backend integration."
                : "Add an internal note. Use @ to mention a teammate."
            }
            aria-label="Case note"
            className="focus-ring w-full resize-none rounded-lg border border-input bg-surface p-3 text-sm placeholder:text-muted-foreground disabled:opacity-60"
          />
          <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
            <label className="flex items-center gap-2 text-xs text-muted-foreground">
              <input
                type="checkbox"
                checked={internal}
                disabled={disabled}
                onChange={(event) => setInternal(event.target.checked)}
                className="size-3.5 accent-[var(--primary)]"
              />
              Internal note (not visible to reporters)
            </label>
            <Button
              size="sm"
              disabled={disabled || !draft.trim()}
              onClick={() => {
                onSubmit?.(draft, internal);
                setDraft("");
              }}
              title={disabled ? "Available after backend integration." : undefined}
            >
              <Send className="size-4" aria-hidden="true" />
              Post note
            </Button>
          </div>
        </div>
      </div>

      <aside className="space-y-3">
        <div className="rounded-xl border border-border/70 bg-card/40 p-3">
          <p className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
            <Eye className="size-3.5" aria-hidden="true" />
            Watchers
          </p>
          {watchers.length === 0 ? (
            <p className="mt-2 text-xs text-muted-foreground">No watchers on this case.</p>
          ) : (
            <ul className="mt-2 space-y-2">
              {watchers.map((watcher) => (
                <li key={watcher.id} className="flex items-center gap-2 text-xs">
                  <span className="grid size-6 place-items-center rounded-full bg-primary/10 text-[10px] font-semibold text-primary">
                    {watcher.displayName.slice(0, 2).toUpperCase()}
                  </span>
                  <span className="min-w-0">
                    <span className="block truncate font-medium text-foreground">
                      {watcher.displayName}
                    </span>
                    <span className="block truncate text-muted-foreground">{watcher.role}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
          <Button
            variant="outline"
            size="sm"
            className="mt-3 w-full"
            disabled
            title="Available after backend integration."
          >
            <Users className="size-4" aria-hidden="true" />
            Manage watchers
          </Button>
        </div>
      </aside>
    </div>
  );
}
