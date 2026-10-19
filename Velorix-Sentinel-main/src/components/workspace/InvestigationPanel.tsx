import {
  BrainCircuit,
  ClipboardList,
  Crosshair,
  FileSearch,
  Fingerprint,
  History,
  LayoutDashboard,
  Radar,
  StickyNote,
} from "lucide-react";
import { useState } from "react";

import { AlertCategoryChip, AlertSeverityChip, AlertStatusChip, RiskScore } from "./AlertChips";
import { AwaitingTelemetry } from "./AwaitingTelemetry";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useAnalystWorkspace } from "@/hooks/useAnalystWorkspace";
import { cn } from "@/lib/utils";
import type { SocAlert } from "@/types/soc";

const TABS = [
  { id: "overview", label: "Overview", icon: LayoutDashboard },
  { id: "evidence", label: "Evidence", icon: FileSearch },
  { id: "timeline", label: "Timeline", icon: History },
  { id: "intel", label: "Threat Intel", icon: Radar },
  { id: "mitre", label: "MITRE ATT&CK", icon: Crosshair },
  { id: "ioc", label: "IOC", icon: Fingerprint },
  { id: "ai", label: "AI Analysis", icon: BrainCircuit },
  { id: "notes", label: "Notes", icon: StickyNote },
  { id: "tasks", label: "Tasks", icon: ClipboardList },
] as const;

type TabId = (typeof TABS)[number]["id"];

const AWAITING: Record<
  Exclude<TabId, "overview" | "notes" | "tasks">,
  { title: string; detail: string }
> = {
  evidence: {
    title: "No evidence collected.",
    detail:
      "Packet extracts, payload samples and artefacts attach here once the inspection worker publishes them.",
  },
  timeline: {
    title: "No investigation events.",
    detail:
      "Detection, triage, containment and analyst actions are ordered here from the SOC event log.",
  },
  intel: {
    title: "No intelligence enrichment.",
    detail:
      "Reputation, actor attribution and campaign context arrive from the threat intelligence feed.",
  },
  mitre: {
    title: "No ATT&CK mapping.",
    detail: "Tactics and techniques are mapped by the threat engine when an alert is correlated.",
  },
  ioc: {
    title: "No indicators extracted.",
    detail:
      "Addresses, domains, hashes and JA3 fingerprints are extracted during deep packet inspection.",
  },
  ai: {
    title: "AI analysis not available.",
    detail:
      "The Security Copilot summarises this alert once the AI backend is connected. No narrative is simulated.",
  },
};

interface InvestigationPanelProps {
  alert: SocAlert | null;
  className?: string;
}

/** Center investigation workspace: tabbed analyst view of the selected alert. */
export function InvestigationPanel({ alert, className }: InvestigationPanelProps) {
  const [tab, setTab] = useState<TabId>("overview");
  const { notes, tasks, addNote, removeNote, addTask, toggleTask, removeTask } =
    useAnalystWorkspace();
  const [noteDraft, setNoteDraft] = useState("");
  const [taskDraft, setTaskDraft] = useState("");

  return (
    <div className={cn("flex min-h-0 flex-col gap-3", className)}>
      <nav aria-label="Investigation tabs" className="-mx-1 flex gap-1 overflow-x-auto px-1 pb-1">
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => setTab(item.id)}
            aria-current={tab === item.id ? "true" : undefined}
            className={cn(
              "focus-ring flex shrink-0 items-center gap-1.5 rounded-full border px-3 py-1.5 text-[11px] transition-colors",
              tab === item.id
                ? "border-primary/40 bg-primary/15 text-primary"
                : "border-border text-muted-foreground hover:border-primary/30 hover:text-foreground",
            )}
          >
            <item.icon className="size-3.5" aria-hidden="true" />
            {item.label}
          </button>
        ))}
      </nav>

      <div className="min-h-0 flex-1 overflow-y-auto pe-0.5">
        {tab === "overview" &&
          (alert ? (
            <div className="space-y-3">
              <div className="rounded-xl border border-border/70 bg-card/40 p-4">
                <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-3">
                  <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold">{alert.name}</h3>
                    <p className="font-mono text-[11px] text-muted-foreground">{alert.reference}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1.5">
                    <AlertSeverityChip severity={alert.severity} />
                    <AlertStatusChip status={alert.status} />
                  </div>
                </div>
                {alert.description && (
                  <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
                    {alert.description}
                  </p>
                )}
              </div>

              <dl className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                <Fact label="Source" value={joinAddress(alert.sourceAddress, alert.sourcePort)} />
                <Fact
                  label="Destination"
                  value={joinAddress(alert.destinationAddress, alert.destinationPort)}
                />
                <Fact label="Protocol" value={alert.protocol ?? "—"} />
                <Fact label="Detector" value={alert.detector ?? "—"} />
                <Fact label="Assigned" value={alert.assignee?.displayName ?? "Unassigned"} />
                <Fact label="Observed" value={formatTime(alert.observedAt ?? alert.createdAt)} />
              </dl>

              <div className="flex items-center gap-3 rounded-xl border border-border/70 bg-card/40 p-3">
                <span className="text-[11px] text-muted-foreground">Risk score</span>
                <RiskScore score={alert.riskScore} />
                <AlertCategoryChip category={alert.category} />
              </div>
            </div>
          ) : (
            <AwaitingTelemetry
              title="No investigations running."
              detail="Select an alert from the triage queue or the live stream to open it here. Investigations start once detections arrive from the backend."
            />
          ))}

        {tab !== "overview" && tab !== "notes" && tab !== "tasks" && (
          <AwaitingTelemetry title={AWAITING[tab].title} detail={AWAITING[tab].detail} />
        )}

        {tab === "notes" && (
          <div className="space-y-3">
            <form
              className="space-y-2"
              onSubmit={(event) => {
                event.preventDefault();
                addNote(noteDraft);
                setNoteDraft("");
              }}
            >
              <Textarea
                rows={3}
                value={noteDraft}
                onChange={(event) => setNoteDraft(event.target.value)}
                placeholder="Investigation note — observations, hypotheses, next steps…"
                aria-label="New investigation note"
              />
              <div className="flex justify-end">
                <Button type="submit" size="sm" disabled={!noteDraft.trim()}>
                  Save note
                </Button>
              </div>
            </form>
            {notes.length === 0 ? (
              <AwaitingTelemetry
                compact
                icon={StickyNote}
                title="No notes yet."
                detail="Notes are stored on this device until the analyst workspace backend ships."
              />
            ) : (
              <ul className="space-y-2">
                {notes.map((note) => (
                  <li
                    key={note.id}
                    className="flex items-start justify-between gap-3 rounded-xl border border-border/70 bg-card/40 p-3"
                  >
                    <div className="min-w-0">
                      <p className="text-xs whitespace-pre-wrap">{note.body}</p>
                      <p className="mt-1 font-mono text-[10px] text-muted-foreground">
                        {formatTime(note.updatedAt)}
                      </p>
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-7 px-2 text-[11px]"
                      onClick={() => removeNote(note.id)}
                    >
                      Delete
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {tab === "tasks" && (
          <div className="space-y-3">
            <form
              className="flex gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                addTask(taskDraft);
                setTaskDraft("");
              }}
            >
              <Input
                value={taskDraft}
                onChange={(event) => setTaskDraft(event.target.value)}
                placeholder="Add a triage or response task…"
                aria-label="New task"
              />
              <Button type="submit" size="sm" disabled={!taskDraft.trim()}>
                Add
              </Button>
            </form>
            {tasks.length === 0 ? (
              <AwaitingTelemetry
                compact
                icon={ClipboardList}
                title="No tasks on your checklist."
                detail="Track playbook steps for the alert you are working."
              />
            ) : (
              <ul className="space-y-1.5">
                {tasks.map((task) => (
                  <li
                    key={task.id}
                    className="flex items-center gap-3 rounded-xl border border-border/70 bg-card/40 px-3 py-2"
                  >
                    <Checkbox
                      checked={task.done}
                      onCheckedChange={() => toggleTask(task.id)}
                      aria-label={`Mark "${task.label}" ${task.done ? "incomplete" : "complete"}`}
                    />
                    <span
                      className={cn(
                        "flex-1 text-xs",
                        task.done && "text-muted-foreground line-through",
                      )}
                    >
                      {task.label}
                    </span>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-7 px-2 text-[11px]"
                      onClick={() => removeTask(task.id)}
                    >
                      Remove
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border/70 bg-card/40 p-3">
      <dt className="text-[10px] tracking-wide text-muted-foreground uppercase">{label}</dt>
      <dd className="mt-0.5 font-mono text-xs">{value}</dd>
    </div>
  );
}

function joinAddress(address: string | null, port: number | null) {
  if (!address) return "—";
  return port ? `${address}:${port}` : address;
}

function formatTime(value: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString();
}
