import { createFileRoute } from "@tanstack/react-router";
import { CheckSquare, ClipboardList, Inbox, Pin, StickyNote, Trash2 } from "lucide-react";
import { useState } from "react";

import { AwaitingBackend, IncidentCard, IncidentTable } from "@/components/incidents";
import { PageHeader } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useAnalystWorkspace } from "@/hooks/useAnalystWorkspace";
import { useIncidentResource } from "@/hooks/useIncidentResource";
import { incidentService } from "@/services/incidentService";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/incidents/workspace")({
  head: () => ({
    meta: [
      { title: "Analyst Workspace · Velorix Sentinel" },
      {
        name: "description",
        content: "Focused analyst workspace for active incident investigation.",
      },
      { property: "og:title", content: "Analyst Workspace · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Focused analyst workspace for active incident investigation.",
      },
    ],
  }),
  component: AnalystWorkspacePage,
});

function AnalystWorkspacePage() {
  const {
    notes,
    tasks,
    starredIncidentIds,
    pinnedIncidentIds,
    addNote,
    removeNote,
    addTask,
    toggleTask,
    removeTask,
    clearCompleted,
    toggleStar,
  } = useAnalystWorkspace();

  const [noteDraft, setNoteDraft] = useState("");
  const [taskDraft, setTaskDraft] = useState("");

  const assigned = useIncidentResource((signal) => incidentService.getAssignedToMe(signal));
  const openTasks = tasks.filter((task) => !task.done).length;

  return (
    <div className="space-y-6">
      <PageHeader
        title="My Workspace"
        description="Your personal queue, investigation notes and response task list."
      />

      <IncidentCard
        title="Assigned to me"
        description={`Cases where you are the owning analyst${
          pinnedIncidentIds.length ? ` · ${pinnedIncidentIds.length} pinned` : ""
        }`}
        icon={Inbox}
      >
        <IncidentTable
          incidents={assigned.data ?? []}
          isLoading={assigned.status === "loading"}
          awaiting={assigned.status === "awaiting"}
          starredIds={starredIncidentIds}
          onToggleStar={toggleStar}
          pageSize={8}
          emptyTitle="No cases assigned to you."
          emptyDetail="Cases appear here as soon as the response backend assigns them to your account."
        />
      </IncidentCard>

      <div className="grid gap-4 lg:grid-cols-2">
        <IncidentCard
          title="Investigation Notes"
          description="Private scratchpad, stored on this device"
          icon={StickyNote}
          badge={
            notes.length > 0 ? (
              <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] text-primary">
                {notes.length}
              </span>
            ) : undefined
          }
        >
          <form
            className="space-y-2"
            onSubmit={(event) => {
              event.preventDefault();
              addNote(noteDraft);
              setNoteDraft("");
            }}
          >
            <Textarea
              value={noteDraft}
              onChange={(event) => setNoteDraft(event.target.value)}
              placeholder="Observations, hypotheses, next steps…"
              rows={3}
              aria-label="New investigation note"
            />
            <div className="flex justify-end">
              <Button type="submit" size="sm" disabled={!noteDraft.trim()}>
                Save note
              </Button>
            </div>
          </form>

          {notes.length === 0 ? (
            <AwaitingBackend
              compact
              icon={StickyNote}
              title="No notes yet."
              detail="Notes are stored locally until the workspace backend ships."
            />
          ) : (
            <ul className="space-y-2">
              {notes.map((note) => (
                <li
                  key={note.id}
                  className="group flex items-start justify-between gap-3 rounded-xl border border-border/70 bg-card/40 p-3"
                >
                  <div className="min-w-0">
                    <p className="text-sm whitespace-pre-wrap">{note.body}</p>
                    <p className="mt-1 font-mono text-[11px] text-muted-foreground">
                      {new Date(note.updatedAt).toLocaleString()}
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label="Delete note"
                    onClick={() => removeNote(note.id)}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </IncidentCard>

        <IncidentCard
          title="Response Tasks"
          description={openTasks > 0 ? `${openTasks} open` : "Checklist for the current shift"}
          icon={CheckSquare}
          action={
            tasks.some((task) => task.done) ? (
              <Button variant="ghost" size="sm" onClick={clearCompleted}>
                Clear completed
              </Button>
            ) : undefined
          }
        >
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
              placeholder="Add a response task…"
              aria-label="New response task"
            />
            <Button type="submit" size="sm" disabled={!taskDraft.trim()}>
              Add
            </Button>
          </form>

          {tasks.length === 0 ? (
            <AwaitingBackend
              compact
              icon={ClipboardList}
              title="No tasks on your checklist."
              detail="Playbook steps can be tracked here during an active investigation."
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
                      "flex-1 text-sm",
                      task.done && "text-muted-foreground line-through",
                    )}
                  >
                    {task.label}
                  </span>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label="Remove task"
                    onClick={() => removeTask(task.id)}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </IncidentCard>
      </div>

      <IncidentCard
        title="Pinned Cases"
        description="Quick access to investigations you are tracking"
        icon={Pin}
      >
        {pinnedIncidentIds.length === 0 ? (
          <AwaitingBackend
            compact
            icon={Pin}
            title="Nothing pinned."
            detail="Open a case and choose “Pin to workspace” to keep it here."
          />
        ) : (
          <ul className="flex flex-wrap gap-2">
            {pinnedIncidentIds.map((id) => (
              <li
                key={id}
                className="rounded-full border border-border bg-surface/60 px-3 py-1 font-mono text-xs"
              >
                {id}
              </li>
            ))}
          </ul>
        )}
      </IncidentCard>
    </div>
  );
}
