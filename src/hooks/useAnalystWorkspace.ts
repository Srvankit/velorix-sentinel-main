import { useCallback, useEffect, useState } from "react";

/**
 * Analyst workspace preferences.
 *
 * These are *analyst-owned* artefacts (notes, task checklist, pinned/starred
 * case references) — not incident data. They live in localStorage so the
 * workspace is usable before the backend ships, and the same shape maps onto
 * `/v1/incidents/workspace` later.
 */

const STORAGE_KEY = "velorix.ir.workspace.v1";

export interface WorkspaceTask {
  id: string;
  label: string;
  done: boolean;
  createdAt: string;
}

export interface WorkspaceNote {
  id: string;
  body: string;
  updatedAt: string;
}

export interface WorkspaceState {
  notes: WorkspaceNote[];
  tasks: WorkspaceTask[];
  pinnedIncidentIds: string[];
  starredIncidentIds: string[];
}

const EMPTY: WorkspaceState = {
  notes: [],
  tasks: [],
  pinnedIncidentIds: [],
  starredIncidentIds: [],
};

function read(): WorkspaceState {
  if (typeof window === "undefined") return EMPTY;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return EMPTY;
    const parsed = JSON.parse(raw) as Partial<WorkspaceState>;
    return {
      notes: parsed.notes ?? [],
      tasks: parsed.tasks ?? [],
      pinnedIncidentIds: parsed.pinnedIncidentIds ?? [],
      starredIncidentIds: parsed.starredIncidentIds ?? [],
    };
  } catch {
    return EMPTY;
  }
}

function makeId() {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `id-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function useAnalystWorkspace() {
  const [state, setState] = useState<WorkspaceState>(EMPTY);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setState(read());
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      /* storage full or blocked — workspace stays in-memory for this session */
    }
  }, [state, hydrated]);

  const addNote = useCallback((body: string) => {
    const trimmed = body.trim();
    if (!trimmed) return;
    setState((prev) => ({
      ...prev,
      notes: [{ id: makeId(), body: trimmed, updatedAt: new Date().toISOString() }, ...prev.notes],
    }));
  }, []);

  const removeNote = useCallback((id: string) => {
    setState((prev) => ({ ...prev, notes: prev.notes.filter((note) => note.id !== id) }));
  }, []);

  const addTask = useCallback((label: string) => {
    const trimmed = label.trim();
    if (!trimmed) return;
    setState((prev) => ({
      ...prev,
      tasks: [
        ...prev.tasks,
        { id: makeId(), label: trimmed, done: false, createdAt: new Date().toISOString() },
      ],
    }));
  }, []);

  const toggleTask = useCallback((id: string) => {
    setState((prev) => ({
      ...prev,
      tasks: prev.tasks.map((task) => (task.id === id ? { ...task, done: !task.done } : task)),
    }));
  }, []);

  const removeTask = useCallback((id: string) => {
    setState((prev) => ({ ...prev, tasks: prev.tasks.filter((task) => task.id !== id) }));
  }, []);

  const clearCompleted = useCallback(() => {
    setState((prev) => ({ ...prev, tasks: prev.tasks.filter((task) => !task.done) }));
  }, []);

  const togglePin = useCallback((incidentId: string) => {
    setState((prev) => ({
      ...prev,
      pinnedIncidentIds: prev.pinnedIncidentIds.includes(incidentId)
        ? prev.pinnedIncidentIds.filter((id) => id !== incidentId)
        : [...prev.pinnedIncidentIds, incidentId],
    }));
  }, []);

  const toggleStar = useCallback((incidentId: string) => {
    setState((prev) => ({
      ...prev,
      starredIncidentIds: prev.starredIncidentIds.includes(incidentId)
        ? prev.starredIncidentIds.filter((id) => id !== incidentId)
        : [...prev.starredIncidentIds, incidentId],
    }));
  }, []);

  return {
    ...state,
    hydrated,
    addNote,
    removeNote,
    addTask,
    toggleTask,
    removeTask,
    clearCompleted,
    togglePin,
    toggleStar,
  };
}
