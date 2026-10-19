import {
  FileCode2,
  FileText,
  Fingerprint,
  ImageIcon,
  Network,
  Paperclip,
  ShieldCheck,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useState } from "react";

import { AwaitingBackend } from "./AwaitingBackend";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { EvidenceItem, EvidenceKind } from "@/types/incident";

const KIND_META: Record<EvidenceKind, { icon: LucideIcon; label: string }> = {
  LOG: { icon: FileCode2, label: "Logs" },
  SCREENSHOT: { icon: ImageIcon, label: "Screenshots" },
  NETWORK_CAPTURE: { icon: Network, label: "Network Captures" },
  INDICATOR: { icon: Fingerprint, label: "Indicators" },
  REPORT: { icon: FileText, label: "Reports" },
  ATTACHMENT: { icon: Paperclip, label: "Attachments" },
};

const KINDS = Object.keys(KIND_META) as EvidenceKind[];

function formatSize(bytes: number | null) {
  if (bytes === null) return "—";
  const units = ["B", "KB", "MB", "GB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}

interface EvidenceVaultProps {
  items: EvidenceItem[];
  /** Disabled until the evidence backend ships. */
  onUpload?: () => void;
  className?: string;
}

/**
 * Evidence locker with kind filtering and a chain-of-custody trail per item.
 * Upload is UI-only until `/v1/incidents/{id}/evidence/upload` exists.
 */
export function EvidenceVault({ items, onUpload, className }: EvidenceVaultProps) {
  const [kind, setKind] = useState<EvidenceKind | "ALL">("ALL");
  const [expanded, setExpanded] = useState<string | null>(null);

  const visible = kind === "ALL" ? items : items.filter((item) => item.kind === kind);

  return (
    <div className={cn("space-y-4", className)}>
      <div className="flex flex-wrap items-center gap-2">
        <FilterPill active={kind === "ALL"} onClick={() => setKind("ALL")} label="All evidence" />
        {KINDS.map((value) => (
          <FilterPill
            key={value}
            active={kind === value}
            onClick={() => setKind(value)}
            label={KIND_META[value].label}
            icon={KIND_META[value].icon}
          />
        ))}
        <Button
          variant="outline"
          size="sm"
          className="ml-auto"
          disabled
          onClick={onUpload}
          title="Available after backend integration."
        >
          <Paperclip className="size-4" aria-hidden="true" />
          Attach evidence
        </Button>
      </div>

      {visible.length === 0 ? (
        <AwaitingBackend
          icon={ShieldCheck}
          title="No evidence has been collected."
          detail="Logs, screenshots, packet captures, indicators, reports and attachments appear here with a full chain-of-custody trail once the evidence backend is connected."
        />
      ) : (
        <ul className="space-y-2">
          {visible.map((item) => {
            const Icon = KIND_META[item.kind].icon;
            const open = expanded === item.id;
            return (
              <li
                key={item.id}
                className="rounded-xl border border-border/70 bg-card/40 transition-colors hover:border-primary/30"
              >
                <button
                  type="button"
                  onClick={() => setExpanded(open ? null : item.id)}
                  aria-expanded={open}
                  className="focus-ring flex w-full items-center gap-3 rounded-xl px-4 py-3 text-left"
                >
                  <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
                    <Icon className="size-4" aria-hidden="true" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{item.name}</span>
                    <span className="block font-mono text-[11px] text-muted-foreground">
                      {item.sha256 ? `sha256:${item.sha256.slice(0, 24)}…` : "hash pending"} ·{" "}
                      {formatSize(item.sizeBytes)}
                    </span>
                  </span>
                  <span className="shrink-0 text-[11px] text-muted-foreground">
                    {new Date(item.collectedAt).toLocaleString()}
                  </span>
                </button>
                {open && (
                  <div className="border-t border-border/70 px-4 py-3">
                    <p className="mb-2 text-xs font-medium text-muted-foreground">
                      Chain of custody
                    </p>
                    {item.custody.length === 0 ? (
                      <p className="text-xs text-muted-foreground">
                        No custody entries recorded yet.
                      </p>
                    ) : (
                      <ol className="space-y-2">
                        {item.custody.map((entry) => (
                          <li key={entry.id} className="flex items-start gap-2 text-xs">
                            <span
                              className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary"
                              aria-hidden="true"
                            />
                            <span>
                              <span className="font-medium text-foreground">{entry.action}</span>
                              {entry.actor && (
                                <span className="text-muted-foreground">
                                  {" "}
                                  · {entry.actor.displayName}
                                </span>
                              )}
                              <span className="block font-mono text-[11px] text-muted-foreground">
                                {new Date(entry.occurredAt).toLocaleString()}
                                {entry.hash ? ` · ${entry.hash.slice(0, 16)}…` : ""}
                              </span>
                            </span>
                          </li>
                        ))}
                      </ol>
                    )}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function FilterPill({
  active,
  onClick,
  label,
  icon: Icon,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  icon?: LucideIcon;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "focus-ring inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs transition-colors",
        active
          ? "border-primary/40 bg-primary/15 font-medium text-primary"
          : "border-border text-muted-foreground hover:border-primary/30 hover:text-foreground",
      )}
    >
      {Icon && <Icon className="size-3.5" aria-hidden="true" />}
      {label}
    </button>
  );
}
