import { useQuery } from "@tanstack/react-query";

import { dashboardService } from "@/services";
import type { ApiError } from "@/types/api";

/** Query keys for every dashboard read, so mutations elsewhere can invalidate precisely. */
export const dashboardKeys = {
  all: ["dashboard"] as const,
  summary: () => [...dashboardKeys.all, "summary"] as const,
  charts: () => [...dashboardKeys.all, "charts"] as const,
  recentAnalyses: (limit: number) => [...dashboardKeys.all, "recent-analyses", limit] as const,
  recentThreats: (limit: number) => [...dashboardKeys.all, "recent-threats", limit] as const,
  activity: (limit: number) => [...dashboardKeys.all, "activity", limit] as const,
  systemHealth: () => [...dashboardKeys.all, "system-health"] as const,
};

const STALE_TIME = 30_000;

export function useDashboardSummary() {
  return useQuery<Awaited<ReturnType<typeof dashboardService.summary>>, ApiError>({
    queryKey: dashboardKeys.summary(),
    queryFn: ({ signal }) => dashboardService.summary(signal),
    staleTime: STALE_TIME,
  });
}

export function useDashboardCharts() {
  return useQuery<Awaited<ReturnType<typeof dashboardService.charts>>, ApiError>({
    queryKey: dashboardKeys.charts(),
    queryFn: ({ signal }) => dashboardService.charts(signal),
    staleTime: STALE_TIME,
  });
}

export function useRecentAnalyses(limit = 10) {
  return useQuery<Awaited<ReturnType<typeof dashboardService.recentAnalyses>>, ApiError>({
    queryKey: dashboardKeys.recentAnalyses(limit),
    queryFn: ({ signal }) => dashboardService.recentAnalyses(limit, signal),
    staleTime: STALE_TIME,
  });
}

export function useRecentThreats(limit = 10) {
  return useQuery<Awaited<ReturnType<typeof dashboardService.recentThreats>>, ApiError>({
    queryKey: dashboardKeys.recentThreats(limit),
    queryFn: ({ signal }) => dashboardService.recentThreats(limit, signal),
    staleTime: STALE_TIME,
  });
}

export function useActivityFeed(limit = 10) {
  return useQuery<Awaited<ReturnType<typeof dashboardService.activity>>, ApiError>({
    queryKey: dashboardKeys.activity(limit),
    queryFn: ({ signal }) => dashboardService.activity(limit, signal),
    staleTime: STALE_TIME,
  });
}

export function useSystemHealth() {
  return useQuery<Awaited<ReturnType<typeof dashboardService.systemHealth>>, ApiError>({
    queryKey: dashboardKeys.systemHealth(),
    queryFn: ({ signal }) => dashboardService.systemHealth(signal),
    staleTime: 15_000,
    refetchInterval: 60_000,
  });
}
