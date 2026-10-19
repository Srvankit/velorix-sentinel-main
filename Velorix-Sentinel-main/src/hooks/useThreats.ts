import { useQuery } from "@tanstack/react-query";

import { threatService } from "@/services/threatService";
import type { ApiError } from "@/types/api";
import type { ThreatQuery } from "@/types/threats";

/** Query keys for every detection engine read. */
export const threatKeys = {
  all: ["threats"] as const,
  list: (query: ThreatQuery) => [...threatKeys.all, "list", query] as const,
  stats: (analysisId?: string) => [...threatKeys.all, "stats", analysisId ?? "all"] as const,
  rules: () => [...threatKeys.all, "rules"] as const,
  detail: (id: string) => [...threatKeys.all, "detail", id] as const,
};

const STALE_TIME = 30_000;

export function useThreats(query: ThreatQuery = {}) {
  return useQuery<Awaited<ReturnType<typeof threatService.list>>, ApiError>({
    queryKey: threatKeys.list(query),
    queryFn: ({ signal }) => threatService.list(query, signal),
    staleTime: STALE_TIME,
  });
}

export function useThreatStats(analysisId?: string) {
  return useQuery<Awaited<ReturnType<typeof threatService.stats>>, ApiError>({
    queryKey: threatKeys.stats(analysisId),
    queryFn: ({ signal }) => threatService.stats(analysisId, signal),
    staleTime: STALE_TIME,
  });
}

export function useDetectionRules() {
  return useQuery<Awaited<ReturnType<typeof threatService.rules>>, ApiError>({
    queryKey: threatKeys.rules(),
    queryFn: ({ signal }) => threatService.rules(signal),
    staleTime: 5 * 60_000,
  });
}
