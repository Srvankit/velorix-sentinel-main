import { motion } from "framer-motion";
import { Sparkle } from "lucide-react";

import { cn } from "@/lib/utils";
import { PROMPT_SUGGESTIONS } from "./prompt-suggestions";

const CATEGORY_TONE = {
  Posture: "text-primary",
  Operations: "text-info",
  Knowledge: "text-success",
  Reporting: "text-warning",
} as const;

/** Clickable starter prompt cards for a fresh conversation. */
export function PromptSuggestions({
  onSelect,
  className,
}: {
  onSelect: (prompt: string) => void;
  className?: string;
}) {
  return (
    <div className={cn("grid gap-3 sm:grid-cols-2 xl:grid-cols-3", className)}>
      {PROMPT_SUGGESTIONS.map((suggestion, index) => (
        <motion.button
          key={suggestion.id}
          type="button"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: index * 0.03, ease: [0.22, 1, 0.36, 1] }}
          onClick={() => onSelect(suggestion.prompt)}
          className="focus-ring group glass-panel rounded-2xl p-4 text-left transition-all hover:-translate-y-0.5 hover:border-primary/40"
        >
          <span className="flex items-center gap-2">
            <Sparkle
              className={cn("size-3.5 shrink-0", CATEGORY_TONE[suggestion.category])}
              aria-hidden="true"
            />
            <span className="text-[10px] font-medium tracking-wider text-muted-foreground uppercase">
              {suggestion.category}
            </span>
          </span>
          <span className="mt-2 block text-sm font-medium tracking-tight">{suggestion.label}</span>
          <span className="mt-1 block line-clamp-2 text-xs text-muted-foreground">
            {suggestion.prompt}
          </span>
        </motion.button>
      ))}
    </div>
  );
}
