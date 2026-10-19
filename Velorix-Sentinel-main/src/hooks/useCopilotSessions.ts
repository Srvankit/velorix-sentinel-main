import { useCallback, useEffect, useMemo, useState } from "react";

import type { CopilotMessage, CopilotSession, CopilotStore } from "@/types/copilot";

const STORAGE_KEY = "velorix.copilot.sessions";

function nowIso() {
  return new Date().toISOString();
}

function createId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return `id-${Math.random().toString(36).slice(2)}-${Date.now()}`;
}

function readStore(): CopilotSession[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as CopilotStore;
    if (!parsed || !Array.isArray(parsed.sessions)) return [];
    return parsed.sessions;
  } catch {
    return [];
  }
}

function writeStore(sessions: CopilotSession[]) {
  if (typeof window === "undefined") return;
  try {
    const payload: CopilotStore = { version: 1, sessions };
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    /* storage full or unavailable — the UI keeps working in memory */
  }
}

export function createSession(title = "New conversation"): CopilotSession {
  const timestamp = nowIso();
  return {
    id: createId(),
    title,
    pinned: false,
    createdAt: timestamp,
    updatedAt: timestamp,
    messages: [],
  };
}

/** Derive a readable session title from the first question asked. */
function deriveTitle(prompt: string): string {
  const clean = prompt.trim().replace(/\s+/g, " ");
  return clean.length > 52 ? `${clean.slice(0, 52)}…` : clean || "New conversation";
}

/**
 * Browser-persisted copilot conversations.
 *
 * Storage is intentionally local: no backend AI service exists yet, so nothing is
 * transmitted. When the AI service lands, replace the mutators with API calls —
 * the component contract stays identical.
 */
export function useCopilotSessions() {
  const [sessions, setSessions] = useState<CopilotSession[]>([]);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setSessions(readStore());
    setHydrated(true);
  }, []);

  const persist = useCallback((next: CopilotSession[]) => {
    setSessions(next);
    writeStore(next);
    return next;
  }, []);

  const update = useCallback((updater: (current: CopilotSession[]) => CopilotSession[]) => {
    setSessions((current) => {
      const next = updater(current);
      writeStore(next);
      return next;
    });
  }, []);

  const startSession = useCallback((): CopilotSession => {
    const session = createSession();
    update((current) => [session, ...current]);
    return session;
  }, [update]);

  const deleteSession = useCallback(
    (id: string) => update((current) => current.filter((session) => session.id !== id)),
    [update],
  );

  const renameSession = useCallback(
    (id: string, title: string) =>
      update((current) =>
        current.map((session) =>
          session.id === id
            ? { ...session, title: title.trim() || session.title, updatedAt: nowIso() }
            : session,
        ),
      ),
    [update],
  );

  const togglePinned = useCallback(
    (id: string) =>
      update((current) =>
        current.map((session) =>
          session.id === id ? { ...session, pinned: !session.pinned } : session,
        ),
      ),
    [update],
  );

  const appendMessages = useCallback(
    (id: string, messages: CopilotMessage[]) =>
      update((current) =>
        current.map((session) =>
          session.id === id
            ? {
                ...session,
                title:
                  session.messages.length === 0 && messages[0]?.role === "user"
                    ? deriveTitle(messages[0].content)
                    : session.title,
                messages: [...session.messages, ...messages],
                updatedAt: nowIso(),
              }
            : session,
        ),
      ),
    [update],
  );

  const clearAll = useCallback(() => persist([]), [persist]);

  const ordered = useMemo(
    () =>
      [...sessions].sort((a, b) => {
        if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
        return b.updatedAt.localeCompare(a.updatedAt);
      }),
    [sessions],
  );

  return {
    sessions: ordered,
    hydrated,
    startSession,
    deleteSession,
    renameSession,
    togglePinned,
    appendMessages,
    clearAll,
  };
}

export const copilotMessage = {
  user(content: string): CopilotMessage {
    return { id: createId(), role: "user", content, createdAt: nowIso() };
  },
  /**
   * Platform notice — used while the AI backend is unavailable. This is never a
   * simulated model answer: it states plainly that no model is connected.
   */
  pendingBackend(): CopilotMessage {
    return {
      id: createId(),
      role: "system",
      notice: true,
      createdAt: nowIso(),
      content:
        "**No AI model is connected yet.**\n\nYour question has been saved to this conversation. Velorix Sentinel will answer it as soon as the AI inference service is wired to `/api/v1/ai/chat`. Nothing was sent to a third party.",
    };
  },
};
