import { api } from "./apiClient";
import type { PageResponse } from "@/types/api";
import type {
  Analysis,
  AnalysisDetail,
  AnalysisQuery,
  AnalysisThreatSummary,
  Packet,
  PacketDetail,
  PacketQuery,
} from "@/types/analysis";

/** Deep packet inspection endpoints exposed by `/api/v1/analysis`. */
export const ANALYSIS_ENDPOINTS = {
  base: "/v1/analysis",
  start: (fileId: string) => `/v1/analysis/start/${fileId}`,
  byId: (id: string) => `/v1/analysis/${id}`,
  summary: (id: string) => `/v1/analysis/${id}/summary`,
  packets: (id: string) => `/v1/analysis/${id}/packets`,
  packetById: (id: string, packetId: string) => `/v1/analysis/${id}/packets/${packetId}`,
} as const;

export const analysisService = {
  /** POST /api/v1/analysis/start/{fileId} — queues an inspection run. */
  start: (fileId: string) => api.post<Analysis>(ANALYSIS_ENDPOINTS.start(fileId)),

  /** GET /api/v1/analysis */
  list: (query: AnalysisQuery = {}, signal?: AbortSignal) =>
    api.get<PageResponse<Analysis>>(ANALYSIS_ENDPOINTS.base, { params: { ...query }, signal }),

  /** GET /api/v1/analysis/{id} */
  getById: (id: string, signal?: AbortSignal) =>
    api.get<AnalysisDetail>(ANALYSIS_ENDPOINTS.byId(id), { signal }),

  /** GET /api/v1/analysis/{id}/summary — threat verdict, null before completion. */
  threatSummary: (id: string, signal?: AbortSignal) =>
    api.get<AnalysisThreatSummary | null>(ANALYSIS_ENDPOINTS.summary(id), { signal }),

  /** GET /api/v1/analysis/{id}/packets */
  packets: (id: string, query: PacketQuery = {}, signal?: AbortSignal) =>
    api.get<PageResponse<Packet>>(ANALYSIS_ENDPOINTS.packets(id), {
      params: {
        protocol: query.protocol,
        networkProtocol: query.networkProtocol,
        suspicious: query.suspicious,
        search: query.search || undefined,
        page: query.page,
        size: query.size,
        sort: query.sort,
        direction: query.direction,
      },
      signal,
    }),

  /** GET /api/v1/analysis/{id}/packets/{packetId} */
  packet: (id: string, packetId: string, signal?: AbortSignal) =>
    api.get<PacketDetail>(ANALYSIS_ENDPOINTS.packetById(id, packetId), { signal }),
};
