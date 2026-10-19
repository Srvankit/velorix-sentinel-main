import { api } from "./apiClient";
import type {
  ActivityEntry,
  DashboardCharts,
  DashboardSummary,
  RecentAnalysis,
  SystemHealth,
  ThreatRecord,
} from "@/types/dashboard";

/** Security operations dashboard endpoints exposed by `/api/v1/dashboard`. */
export const DASHBOARD_ENDPOINTS = {
  summary: "/v1/dashboard/summary",
  charts: "/v1/dashboard/charts",
  recentAnalysis: "/v1/dashboard/recent-analysis",
  recentThreats: "/v1/dashboard/recent-threats",
  activity: "/v1/dashboard/activity",
  systemHealth: "/v1/dashboard/system-health",
} as const;

export const dashboardService = {
  summary: (signal?: AbortSignal) =>
    api.get<DashboardSummary>(DASHBOARD_ENDPOINTS.summary, { signal }),

  charts: (signal?: AbortSignal) =>
    api.get<DashboardCharts>(DASHBOARD_ENDPOINTS.charts, { signal }),

  recentAnalyses: (limit = 10, signal?: AbortSignal) =>
    api.get<RecentAnalysis[]>(DASHBOARD_ENDPOINTS.recentAnalysis, { params: { limit }, signal }),

  recentThreats: (limit = 10, signal?: AbortSignal) =>
    api.get<ThreatRecord[]>(DASHBOARD_ENDPOINTS.recentThreats, { params: { limit }, signal }),

  activity: (limit = 10, signal?: AbortSignal) =>
    api.get<ActivityEntry[]>(DASHBOARD_ENDPOINTS.activity, { params: { limit }, signal }),

  systemHealth: (signal?: AbortSignal) =>
    api.get<SystemHealth>(DASHBOARD_ENDPOINTS.systemHealth, { signal }),
};
