import { Link } from "@tanstack/react-router";
import { Check, MessageSquarePlus, Pencil, Pin, PinOff, Search, Trash2, X } from "lucide-react";
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { CopilotSession } from "@/types/copilot";
import { formatRelativeTime } from "@/utils/format";

interface ConversationSidebarProps {
  sessions: CopilotSession[];
  activeId?: string;
  onNew: () => void;
  onRename: (id: string, title: string) => void;
  onDelete: (id: string) => void;
  onTogglePin: (id: string) => void;
  className?: string;
}

function SessionRow({
  session,
  active,
  onRename,
  onDelete,
  onTogglePin,
}: {
  session: CopilotSession;
  active: boolean;
  onRename: (id: string, title: string) => void;
  onDelete: (id: string) => void;
  onTogglePin: (id: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(session.title);

  if (editing) {
    return (
      <div className="flex items-center gap-1 rounded-lg border border-primary/40 bg-surface px-2 py-1.5">
        <input
          value={draft}
          autoFocus
          aria-label="Conversation title"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              onRename(session.id, draft);
              setEditing(false);
            }
            if (event.key === "Escape") setEditing(false);
          }}
          className="min-w-0 flex-1 bg-transparent text-sm outline-none"
        />
        <Button
          variant="ghost"
          size="icon"
          className="size-6"
          aria-label="Save title"
          onClick={() => {
            onRename(session.id, draft);
            setEditing(false);
          }}
        >
          <Check className="size-3" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="size-6"
          aria-label="Cancel rename"
          onClick={() => setEditing(false)}
        >
          <X className="size-3" />
        </Button>
      </div>
    );
  }

  return (
    <div
      className={cn(
        "group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-1 rounded-lg px-2 py-1.5 transition-colors hover:bg-sidebar-accent",
        active && "bg-sidebar-accent",
      )}
    >
      <Link
        to="/assistant/$sessionId"
        params={{ sessionId: session.id }}
        className="focus-ring min-w-0 rounded-md"
      >
        <span className="block truncate text-sm font-medium">{session.title}</span>
        <span className="block truncate text-[11px] text-muted-foreground">
          {session.messages.length} message{session.messages.length === 1 ? "" : "s"} ·{" "}
          {formatRelativeTime(session.updatedAt)}
        </span>
      </Link>

      <span className="flex shrink-0 items-center opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
        <Button
          variant="ghost"
          size="icon"
          className="size-6"
          aria-label={session.pinned ? "Unpin conversation" : "Pin conversation"}
          onClick={() => onTogglePin(session.id)}
        >
          {session.pinned ? <PinOff className="size-3" /> : <Pin className="size-3" />}
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="size-6"
          aria-label="Rename conversation"
          onClick={() => {
            setDraft(session.title);
            setEditing(true);
          }}
        >
          <Pencil className="size-3" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="size-6 text-destructive"
          aria-label="Delete conversation"
          onClick={() => onDelete(session.id)}
        >
          <Trash2 className="size-3" />
        </Button>
      </span>
    </div>
  );
}

/** Searchable session history with pinned and recent groups. */
export function ConversationSidebar({
  sessions,
  activeId,
  onNew,
  onRename,
  onDelete,
  onTogglePin,
  className,
}: ConversationSidebarProps) {
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return sessions;
    return sessions.filter(
      (session) =>
        session.title.toLowerCase().includes(needle) ||
        session.messages.some((message) => message.content.toLowerCase().includes(needle)),
    );
  }, [sessions, query]);

  const pinned = filtered.filter((session) => session.pinned);
  const recent = filtered.filter((session) => !session.pinned);

  return (
    <div className={cn("glass-panel flex flex-col gap-4 rounded-2xl p-4", className)}>
      <Button onClick={onNew} className="w-full justify-start gap-2">
        <MessageSquarePlus className="size-4" aria-hidden="true" />
        New conversation
      </Button>

      <div className="relative">
        <Search
          className="pointer-events-none absolute top-1/2 left-3 size-3.5 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search conversations"
          aria-label="Search conversations"
          className="focus-ring h-9 w-full rounded-lg border border-input bg-surface pl-9 text-sm placeholder:text-muted-foreground"
        />
      </div>

      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto">
        {filtered.length === 0 && (
          <p className="px-2 py-6 text-center text-xs text-muted-foreground">
            {sessions.length === 0
              ? "No conversations yet. Start one to build your history."
              : "No conversation matches that search."}
          </p>
        )}

        {pinned.length > 0 && (
          <section className="space-y-1">
            <p className="px-2 text-[10px] font-medium tracking-wider text-muted-foreground uppercase">
              Pinned
            </p>
            {pinned.map((session) => (
              <SessionRow
                key={session.id}
                session={session}
                active={session.id === activeId}
                onRename={onRename}
                onDelete={onDelete}
                onTogglePin={onTogglePin}
              />
            ))}
          </section>
        )}

        {recent.length > 0 && (
          <section className="space-y-1">
            <p className="px-2 text-[10px] font-medium tracking-wider text-muted-foreground uppercase">
              Recent
            </p>
            {recent.map((session) => (
              <SessionRow
                key={session.id}
                session={session}
                active={session.id === activeId}
                onRename={onRename}
                onDelete={onDelete}
                onTogglePin={onTogglePin}
              />
            ))}
          </section>
        )}
      </div>
    </div>
  );
}
