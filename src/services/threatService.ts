import { api } from "./apiClient";
import type { PageResponse } from "@/types/api";
import type { DetectionRule, Threat, ThreatQuery, ThreatStats } from "@/types/threats";

/** Detection engine endpoints exposed by `/api/v1/threats`. */
export const THREAT_ENDPOINTS = {
  base: "/v1/threats",
  stats: "/v1/threats/stats",
  rules: "/v1/threats/rules",
  byId: (id: string) => `/v1/threats/${id}`,
} as const;

export const threatService = {
  /** GET /api/v1/threats — owner-scoped, filterable finding feed. */
  list: (query: ThreatQuery = {}, signal?: AbortSignal) =>
    api.get<PageResponse<Threat>>(THREAT_ENDPOINTS.base, {
      params: {
        analysisId: query.analysisId,
        severity: query.severity,
        threatType: query.threatType,
        status: query.status,
        detectionRule: query.detectionRule,
        search: query.search || undefined,
        minConfidence: query.minConfidence,
        page: query.page,
        size: query.size,
        sort: query.sort,
        direction: query.direction,
      },
      signal,
    }),

  /** GET /api/v1/threats/stats — histograms, top talkers and detection timeline. */
  stats: (analysisId?: string, signal?: AbortSignal) =>
    api.get<ThreatStats>(THREAT_ENDPOINTS.stats, {
      params: { analysisId },
      signal,
    }),

  /** GET /api/v1/threats/rules — installed detection rule catalogue. */
  rules: (signal?: AbortSignal) => api.get<DetectionRule[]>(THREAT_ENDPOINTS.rules, { signal }),

  /** GET /api/v1/threats/{id} */
  getById: (id: string, signal?: AbortSignal) =>
    api.get<Threat>(THREAT_ENDPOINTS.byId(id), { signal }),
};
