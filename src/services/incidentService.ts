import { api } from "./apiClient";
import type { PageResponse } from "@/types/api";
import type {
  CreateCaseRequest,
  EvidenceItem,
  Incident,
  IncidentComment,
  IncidentDetail,
  IncidentEvent,
  IncidentMetrics,
  IncidentPriority,
  IncidentQuery,
  IncidentReport,
  IncidentStatus,
} from "@/types/incident";

/**
 * Incident Response endpoints.
 *
 * NOTE: the Spring Boot backend does not implement these yet. The contracts are
 * frozen so the UI can flip from honest "awaiting backend" states to live data
 * by setting `INCIDENT_BACKEND_READY` to true — no component changes required.
 */
export const INCIDENT_ENDPOINTS = {
  metrics: "/v1/incidents/metrics",
  list: "/v1/incidents",
  byId: (id: string) => `/v1/incidents/${id}`,
  create: "/v1/incidents",
  assign: (id: string) => `/v1/incidents/${id}/assign`,
  status: (id: string) => `/v1/incidents/${id}/status`,
  priority: (id: string) => `/v1/incidents/${id}/priority`,
  tags: (id: string) => `/v1/incidents/${id}/tags`,
  watchers: (id: string) => `/v1/incidents/${id}/watchers`,
  timeline: (id: string) => `/v1/incidents/${id}/timeline`,
  comments: (id: string) => `/v1/incidents/${id}/comments`,
  evidence: (id: string) => `/v1/incidents/${id}/evidence`,
  evidenceUpload: (id: string) => `/v1/incidents/${id}/evidence/upload`,
  custody: (id: string, evidenceId: string) => `/v1/incidents/${id}/evidence/${evidenceId}/custody`,
  aiSummary: (id: string) => `/v1/incidents/${id}/ai-summary`,
  runPlaybook: (id: string) => `/v1/incidents/${id}/playbooks/run`,
  report: (id: string) => `/v1/incidents/${id}/report`,
  reportExport: (id: string) => `/v1/incidents/${id}/report/export`,
  queue: "/v1/incidents/queue",
  assignedToMe: "/v1/incidents/assigned",
  bulk: "/v1/incidents/bulk",
} as const;

/**
 * Flip to `true` once the incident endpoints ship. Every screen switches from
 * empty/awaiting states to live data with no further edits.
 */
export const INCIDENT_BACKEND_READY = false;

export const incidentService = {
  getMetrics: (signal?: AbortSignal) =>
    api.get<IncidentMetrics>(INCIDENT_ENDPOINTS.metrics, { signal }),

  list: (query: IncidentQuery = {}, signal?: AbortSignal) =>
    api.get<PageResponse<Incident>>(INCIDENT_ENDPOINTS.list, { params: query, signal }),

  getById: (id: string, signal?: AbortSignal) =>
    api.get<IncidentDetail>(INCIDENT_ENDPOINTS.byId(id), { signal }),

  create: (payload: CreateCaseRequest) => api.post<Incident>(INCIDENT_ENDPOINTS.create, payload),

  assign: (id: string, assigneeId: string) =>
    api.patch<Incident>(INCIDENT_ENDPOINTS.assign(id), { assigneeId }),

  updateStatus: (id: string, status: IncidentStatus, note?: string) =>
    api.patch<Incident>(INCIDENT_ENDPOINTS.status(id), { status, note }),

  updatePriority: (id: string, priority: IncidentPriority) =>
    api.patch<Incident>(INCIDENT_ENDPOINTS.priority(id), { priority }),

  setTags: (id: string, tags: string[]) => api.put<Incident>(INCIDENT_ENDPOINTS.tags(id), { tags }),

  setWatchers: (id: string, watcherIds: string[]) =>
    api.put<Incident>(INCIDENT_ENDPOINTS.watchers(id), { watcherIds }),

  getTimeline: (id: string, signal?: AbortSignal) =>
    api.get<IncidentEvent[]>(INCIDENT_ENDPOINTS.timeline(id), { signal }),

  listComments: (id: string, signal?: AbortSignal) =>
    api.get<IncidentComment[]>(INCIDENT_ENDPOINTS.comments(id), { signal }),

  addComment: (id: string, body: string, internal = true) =>
    api.post<IncidentComment>(INCIDENT_ENDPOINTS.comments(id), { body, internal }),

  listEvidence: (id: string, signal?: AbortSignal) =>
    api.get<EvidenceItem[]>(INCIDENT_ENDPOINTS.evidence(id), { signal }),

  generateAiSummary: (id: string) =>
    api.post<{ summary: string }>(INCIDENT_ENDPOINTS.aiSummary(id)),

  runPlaybook: (id: string, playbookId: string) =>
    api.post<IncidentEvent>(INCIDENT_ENDPOINTS.runPlaybook(id), { playbookId }),

  getReport: (id: string, signal?: AbortSignal) =>
    api.get<IncidentReport>(INCIDENT_ENDPOINTS.report(id), { signal }),

  getQueue: (signal?: AbortSignal) => api.get<Incident[]>(INCIDENT_ENDPOINTS.queue, { signal }),

  getAssignedToMe: (signal?: AbortSignal) =>
    api.get<Incident[]>(INCIDENT_ENDPOINTS.assignedToMe, { signal }),

  bulkUpdate: (ids: string[], patch: { status?: IncidentStatus; assigneeId?: string }) =>
    api.post<{ updated: number }>(INCIDENT_ENDPOINTS.bulk, { ids, ...patch }),
};
