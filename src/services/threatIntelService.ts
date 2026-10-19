import { api } from "./apiClient";
import type { PageResponse } from "@/types/api";
import type { IocRecord } from "@/data/ioc-catalog";
import type {
  CveRecord,
  IntelSource,
  ThreatFeedEvent,
  ThreatLandscape,
  ThreatMapPoint,
  ThreatReport,
  Watchlist,
} from "@/types/intel";

/**
 * Threat Intelligence endpoints.
 *
 * NOTE: none of these are implemented by the Spring Boot backend yet. The
 * contracts are frozen here so the UI can switch from "awaiting live feed" to
 * live data by flipping `INTEL_BACKEND_READY` once the endpoints ship.
 */
export const INTEL_ENDPOINTS = {
  landscape: "/v1/intel/landscape",
  sources: "/v1/intel/sources",
  iocs: "/v1/intel/iocs",
  iocById: (id: string) => `/v1/intel/iocs/${id}`,
  iocImport: "/v1/intel/iocs/import",
  iocExport: "/v1/intel/iocs/export",
  cves: "/v1/intel/cves",
  cveById: (id: string) => `/v1/intel/cves/${id}`,
  mitreCoverage: "/v1/intel/mitre/coverage",
  feed: "/v1/intel/feed",
  map: "/v1/intel/map",
  watchlists: "/v1/intel/watchlists",
  watchlistById: (id: string) => `/v1/intel/watchlists/${id}`,
  reports: "/v1/intel/reports",
  reportById: (id: string) => `/v1/intel/reports/${id}`,
  reportExport: (id: string) => `/v1/intel/reports/${id}/export`,
  search: "/v1/intel/search",
} as const;

/**
 * Flip to `true` (or drive from an env flag) once the intelligence service is
 * deployed. Until then every hook resolves to an explicit "awaiting feed" state
 * instead of inventing data.
 */
export const INTEL_BACKEND_READY = false;

export const threatIntelService = {
  /** GET /api/v1/intel/landscape */
  getLandscape: (signal?: AbortSignal) =>
    api.get<ThreatLandscape>(INTEL_ENDPOINTS.landscape, { signal }),

  /** GET /api/v1/intel/sources */
  listSources: (signal?: AbortSignal) =>
    api.get<IntelSource[]>(INTEL_ENDPOINTS.sources, { signal }),

  /** GET /api/v1/intel/iocs */
  listIocs: (params: Record<string, unknown> = {}, signal?: AbortSignal) =>
    api.get<PageResponse<IocRecord>>(INTEL_ENDPOINTS.iocs, { params, signal }),

  /** GET /api/v1/intel/cves */
  listCves: (params: Record<string, unknown> = {}, signal?: AbortSignal) =>
    api.get<PageResponse<CveRecord>>(INTEL_ENDPOINTS.cves, { params, signal }),

  /** GET /api/v1/intel/feed */
  listFeed: (params: Record<string, unknown> = {}, signal?: AbortSignal) =>
    api.get<PageResponse<ThreatFeedEvent>>(INTEL_ENDPOINTS.feed, { params, signal }),

  /** GET /api/v1/intel/map */
  getMap: (signal?: AbortSignal) => api.get<ThreatMapPoint[]>(INTEL_ENDPOINTS.map, { signal }),

  /** GET /api/v1/intel/watchlists */
  listWatchlists: (signal?: AbortSignal) =>
    api.get<Watchlist[]>(INTEL_ENDPOINTS.watchlists, { signal }),

  /** GET /api/v1/intel/reports */
  listReports: (signal?: AbortSignal) =>
    api.get<ThreatReport[]>(INTEL_ENDPOINTS.reports, { signal }),
};
