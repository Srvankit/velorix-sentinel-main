import { useNavigate } from "@tanstack/react-router";
import { PanelRightClose, PanelRightOpen, Trash2 } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";

import { PageHeader } from "@/components/common";
import { Button } from "@/components/ui/button";
import { copilotMessage, useCopilotSessions } from "@/hooks/useCopilotSessions";
import { cn } from "@/lib/utils";
import type { CopilotSession } from "@/types/copilot";
import { AiCommandCenter } from "./AiCommandCenter";
import { AiStatusPanel } from "./AiStatusPanel";
import { ConversationSidebar } from "./ConversationSidebar";
import { CopilotComposer } from "./CopilotComposer";
import { CopilotEmptyState } from "./CopilotEmptyState";
import { MessageBubble } from "./MessageBubble";
import { ProviderPlaceholders } from "./ProviderPlaceholders";

/**
 * Full-page AI Security Copilot.
 *
 * Conversations are addressable by URL (`/assistant/$sessionId`) and persisted in
 * this browser. No model output is generated locally: submitting a prompt records
 * the question and a clearly-labelled platform notice.
 */
export function CopilotWorkspace({ sessionId }: { sessionId?: string }) {
  const navigate = useNavigate();
  const {
    sessions,
    hydrated,
    startSession,
    deleteSession,
    renameSession,
    togglePinned,
    appendMessages,
  } = useCopilotSessions();
  const [railOpen, setRailOpen] = useState(true);
  const [draft, setDraft] = useState("");
  const endRef = useRef<HTMLDivElement>(null);

  const active: CopilotSession | undefined = useMemo(
    () => sessions.find((session) => session.id === sessionId),
    [sessions, sessionId],
  );

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [active?.messages.length]);

  const newConversation = () => {
    const session = startSession();
    void navigate({ to: "/assistant/$sessionId", params: { sessionId: session.id } });
  };

  const submitPrompt = (prompt: string) => {
    const target = active ?? startSession();
    appendMessages(target.id, [copilotMessage.user(prompt), copilotMessage.pendingBackend()]);
    setDraft("");
    if (!active) {
      void navigate({ to: "/assistant/$sessionId", params: { sessionId: target.id } });
    }
  };

  const removeSession = (id: string) => {
    deleteSession(id);
    if (id === sessionId) void navigate({ to: "/assistant" });
  };

  return (
    <>
      <PageHeader
        title="AI Security Copilot"
        description="Ask analyst-grade questions about detections, captures and security frameworks."
        actions={
          <>
            <span className="hidden items-center gap-2 rounded-full border border-primary/40 bg-primary/10 px-3 py-1 text-xs font-medium text-primary sm:inline-flex">
              <span className="size-1.5 animate-pulse rounded-full bg-current" aria-hidden="true" />
              Ready for AI Integration
            </span>
            {active && (
              <Button variant="outline" size="sm" onClick={() => removeSession(active.id)}>
                <Trash2 className="size-4" aria-hidden="true" />
                Delete chat
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon"
              className="hidden xl:inline-flex"
              aria-label={railOpen ? "Hide intelligence rail" : "Show intelligence rail"}
              onClick={() => setRailOpen((value) => !value)}
            >
              {railOpen ? (
                <PanelRightClose className="size-4" />
              ) : (
                <PanelRightOpen className="size-4" />
              )}
            </Button>
          </>
        }
      />

      <div
        className={cn(
          "mt-6 grid gap-6 lg:grid-cols-[17rem_minmax(0,1fr)]",
          railOpen && "xl:grid-cols-[17rem_minmax(0,1fr)_20rem]",
        )}
      >
        <ConversationSidebar
          sessions={sessions}
          activeId={sessionId}
          onNew={newConversation}
          onRename={renameSession}
          onDelete={removeSession}
          onTogglePin={togglePinned}
          className="max-h-[38rem] lg:sticky lg:top-24 lg:self-start"
        />

        <section className="flex min-w-0 flex-col gap-4">
          <div className="glass-panel min-h-[26rem] rounded-2xl p-4 sm:p-6">
            {!hydrated ? (
              <div className="space-y-4">
                <div className="h-20 animate-pulse rounded-2xl bg-muted/50" />
                <div className="h-20 animate-pulse rounded-2xl bg-muted/40" />
              </div>
            ) : active && active.messages.length > 0 ? (
              <div className="space-y-5">
                {active.messages.map((message) => (
                  <MessageBubble key={message.id} message={message} />
                ))}
                <div ref={endRef} />
              </div>
            ) : (
              <CopilotEmptyState />
            )}
          </div>

          {(!active || active.messages.length === 0) && (
            <PromptSuggestionsLazy onSelect={setDraft} />
          )}

          <CopilotComposer value={draft} onSubmit={submitPrompt} />
        </section>

        {railOpen && (
          <aside className="space-y-6 xl:sticky xl:top-24 xl:self-start">
            <AiStatusPanel />
            <AiCommandCenter />
            <ProviderPlaceholders />
          </aside>
        )}
      </div>
    </>
  );
}

/** Suggestions are only needed on an empty canvas, so they load with it. */
function PromptSuggestionsLazy({ onSelect }: { onSelect: (prompt: string) => void }) {
  const [Component, setComponent] = useState<null | React.ComponentType<{
    onSelect: (prompt: string) => void;
  }>>(null);

  useEffect(() => {
    let mounted = true;
    void import("./PromptSuggestions").then((module) => {
      if (mounted) setComponent(() => module.PromptSuggestions);
    });
    return () => {
      mounted = false;
    };
  }, []);

  if (!Component) {
    return (
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 6 }).map((_, index) => (
          <div key={index} className="h-24 animate-pulse rounded-2xl bg-muted/40" />
        ))}
      </div>
    );
  }

  return <Component onSelect={onSelect} />;
}
