import { CornerDownLeft, Loader2, SendHorizonal } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface CopilotComposerProps {
  onSubmit: (prompt: string) => void;
  value?: string;
  disabled?: boolean;
  busy?: boolean;
  className?: string;
}

/**
 * Prompt composer. Submitting stores the question in the conversation; no model
 * output is fabricated while the AI backend is disconnected.
 */
export function CopilotComposer({
  onSubmit,
  value,
  disabled = false,
  busy = false,
  className,
}: CopilotComposerProps) {
  const [draft, setDraft] = useState(value ?? "");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (value !== undefined) setDraft(value);
  }, [value]);

  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

  const submit = () => {
    const prompt = draft.trim();
    if (!prompt || disabled) return;
    onSubmit(prompt);
    setDraft("");
    textareaRef.current?.focus();
  };

  return (
    <form
      className={cn("glass-panel rounded-2xl p-3", className)}
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <textarea
        ref={textareaRef}
        rows={3}
        value={draft}
        disabled={disabled}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            submit();
          }
        }}
        placeholder="Ask about threats, captures, MITRE techniques, or request a SOC report…"
        aria-label="Ask the security copilot"
        className="focus-ring w-full resize-none rounded-xl bg-transparent p-2 text-sm placeholder:text-muted-foreground disabled:opacity-60"
      />

      <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 pt-1">
        <p className="min-w-0 truncate text-[11px] text-muted-foreground">
          <CornerDownLeft className="mr-1 inline size-3" aria-hidden="true" />
          Enter to send · Shift+Enter for a new line
        </p>
        <Button type="submit" size="sm" disabled={disabled || busy || draft.trim().length === 0}>
          {busy ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <SendHorizonal className="size-4" aria-hidden="true" />
          )}
          Send
        </Button>
      </div>
    </form>
  );
}
