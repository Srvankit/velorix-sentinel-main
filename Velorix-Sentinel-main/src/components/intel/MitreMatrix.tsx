import { ChevronDown } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { useState } from "react";

import { MITRE_TACTICS, type MitreTactic } from "@/data/mitre";
import { cn } from "@/lib/utils";

/** Interactive ATT&CK matrix: tactic columns expanding into technique detail. */
export function MitreMatrix() {
  const [openTactic, setOpenTactic] = useState<string | null>(MITRE_TACTICS[0]?.id ?? null);

  return (
    <div className="space-y-3">
      {MITRE_TACTICS.map((tactic, index) => (
        <TacticRow
          key={tactic.id}
          tactic={tactic}
          index={index}
          open={openTactic === tactic.id}
          onToggle={() => setOpenTactic(openTactic === tactic.id ? null : tactic.id)}
        />
      ))}
    </div>
  );
}

function TacticRow({
  tactic,
  index,
  open,
  onToggle,
}: {
  tactic: MitreTactic;
  index: number;
  open: boolean;
  onToggle: () => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay: Math.min(index * 0.03, 0.3) }}
      className={cn(
        "glass-panel overflow-hidden transition-colors",
        open ? "border-primary/40" : "hover:border-primary/25",
      )}
    >
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="focus-ring flex w-full items-center justify-between gap-4 p-4 text-left"
      >
        <div className="flex min-w-0 items-center gap-3">
          <span className="rounded-md border border-primary/30 bg-primary/10 px-2 py-0.5 font-mono text-[11px] text-primary">
            {tactic.id}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">{tactic.name}</p>
            <p className="truncate text-xs text-muted-foreground">{tactic.objective}</p>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          <span className="hidden text-xs text-muted-foreground sm:inline">
            {tactic.techniques.length} techniques
          </span>
          <ChevronDown
            className={cn(
              "size-4 text-muted-foreground transition-transform",
              open && "rotate-180",
            )}
            aria-hidden="true"
          />
        </div>
      </button>

      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
            className="overflow-hidden"
          >
            <div className="grid gap-3 border-t border-border/70 p-4 lg:grid-cols-2">
              {tactic.techniques.map((technique) => (
                <article
                  key={technique.id}
                  className="rounded-xl border border-border/70 bg-card/40 p-4 transition-colors hover:border-primary/30"
                >
                  <header className="flex items-center justify-between gap-2">
                    <h3 className="text-sm font-semibold">
                      <span className="mr-2 font-mono text-xs text-primary">{technique.id}</span>
                      {technique.name}
                    </h3>
                    <span
                      className={cn(
                        "shrink-0 rounded-full border px-2 py-0.5 text-[11px]",
                        technique.coverage === "in-progress"
                          ? "border-amber-500/40 bg-amber-500/10 text-amber-400"
                          : "border-border bg-muted/40 text-muted-foreground",
                      )}
                    >
                      {technique.coverage === "in-progress"
                        ? "Coverage in progress"
                        : "Coverage planned"}
                    </span>
                  </header>
                  <p className="mt-2 text-xs leading-relaxed text-muted-foreground">
                    {technique.description}
                  </p>
                  <dl className="mt-3 space-y-2 text-xs">
                    <div>
                      <dt className="font-medium text-foreground">Detection notes</dt>
                      <dd className="text-muted-foreground">{technique.detection}</dd>
                    </div>
                    <div>
                      <dt className="font-medium text-foreground">Mitigation</dt>
                      <dd className="text-muted-foreground">{technique.mitigation}</dd>
                    </div>
                  </dl>
                </article>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
