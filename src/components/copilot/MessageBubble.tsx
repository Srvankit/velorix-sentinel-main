import { motion } from "framer-motion";
import { BotMessageSquare, Check, Copy, Download, Info, UserRound } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { CopilotMessage } from "@/types/copilot";
import { formatDateTime } from "@/utils/format";
import { MarkdownRenderer } from "./MarkdownRenderer";

function downloadMarkdown(message: CopilotMessage) {
  const blob = new Blob([message.content], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `velorix-copilot-${message.id.slice(0, 8)}.md`;
  anchor.click();
  URL.revokeObjectURL(url);
}

/** One conversation turn: user prompt, model answer, or platform notice. */
export function MessageBubble({ message }: { message: CopilotMessage }) {
  const [copied, setCopied] = useState(false);
  const isUser = message.role === "user";

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  };

  return (
    <motion.article
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
      className={cn("flex gap-3", isUser && "flex-row-reverse")}
    >
      <span
        className={cn(
          "grid size-8 shrink-0 place-items-center rounded-lg border border-border",
          isUser
            ? "bg-muted text-muted-foreground"
            : message.notice
              ? "bg-info/15 text-info"
              : "bg-gradient-brand text-primary-foreground",
        )}
        aria-hidden="true"
      >
        {isUser ? (
          <UserRound className="size-4" />
        ) : message.notice ? (
          <Info className="size-4" />
        ) : (
          <BotMessageSquare className="size-4" />
        )}
      </span>

      <div className={cn("min-w-0 max-w-[46rem] flex-1", isUser && "flex flex-col items-end")}>
        <div
          className={cn(
            "min-w-0 rounded-2xl border px-4 py-3",
            isUser
              ? "border-primary/30 bg-primary/10 text-foreground"
              : message.notice
                ? "border-info/30 bg-info/5"
                : "border-border bg-surface",
          )}
        >
          <MarkdownRenderer content={message.content} />
        </div>

        <div
          className={cn(
            "mt-1.5 flex items-center gap-1 text-[11px] text-muted-foreground",
            isUser && "flex-row-reverse",
          )}
        >
          <time dateTime={message.createdAt}>{formatDateTime(message.createdAt)}</time>
          {message.model && <span className="hidden sm:inline">· {message.model}</span>}
          <Button
            variant="ghost"
            size="icon"
            className="size-6"
            aria-label="Copy message"
            onClick={() => void copy()}
          >
            {copied ? <Check className="size-3" /> : <Copy className="size-3" />}
          </Button>
          {!isUser && (
            <Button
              variant="ghost"
              size="icon"
              className="size-6"
              aria-label="Export message as markdown"
              onClick={() => downloadMarkdown(message)}
            >
              <Download className="size-3" />
            </Button>
          )}
        </div>
      </div>
    </motion.article>
  );
}
