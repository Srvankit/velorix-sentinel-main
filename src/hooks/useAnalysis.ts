import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { analysisService } from "@/services/analysisService";
import type { ApiError } from "@/types/api";
import type { AnalysisQuery, PacketQuery } from "@/types/analysis";

/** Query keys for every inspection read, so a started run can invalidate precisely. */
export const analysisKeys = {
  all: ["analysis"] as const,
  list: (query: AnalysisQuery) => [...analysisKeys.all, "list", query] as const,
  detail: (id: string) => [...analysisKeys.all, "detail", id] as const,
  summary: (id: string) => [...analysisKeys.all, "summary", id] as const,
  packets: (id: string, query: PacketQuery) => [...analysisKeys.all, "packets", id, query] as const,
  packet: (id: string, packetId: string) => [...analysisKeys.all, "packet", id, packetId] as const,
};

/** Runs still in the pipeline are polled; finished runs are static. */
const LIVE_POLL_MS = 4_000;

export function useAnalysisList(query: AnalysisQuery) {
  return useQuery({
    queryKey: analysisKeys.list(query),
    queryFn: ({ signal }) => analysisService.list(query, signal),
    staleTime: 10_000,
    refetchInterval: (result) =>
      result.state.data?.content.some(
        (item) => item.status === "QUEUED" || item.status === "PROCESSING",
      )
        ? LIVE_POLL_MS
        : false,
  });
}

export function useAnalysisDetail(id: string | null) {
  return useQuery({
    queryKey: analysisKeys.detail(id ?? "none"),
    queryFn: ({ signal }) => analysisService.getById(id as string, signal),
    enabled: Boolean(id),
    refetchInterval: (result) => {
      const status = result.state.data?.analysis.status;
      return status === "QUEUED" || status === "PROCESSING" ? LIVE_POLL_MS : false;
    },
  });
}

/** GET /api/v1/analysis/{id}/summary — the threat verdict for a run. */
export function useThreatSummary(id: string | null, live: boolean) {
  return useQuery({
    queryKey: analysisKeys.summary(id ?? "none"),
    queryFn: ({ signal }) => analysisService.threatSummary(id as string, signal),
    enabled: Boolean(id),
    refetchInterval: live ? LIVE_POLL_MS : false,
  });
}

export function usePackets(id: string | null, query: PacketQuery) {
  return useQuery({
    queryKey: analysisKeys.packets(id ?? "none", query),
    queryFn: ({ signal }) => analysisService.packets(id as string, query, signal),
    enabled: Boolean(id),
    placeholderData: (previous) => previous,
  });
}

export function usePacketDetail(analysisId: string | null, packetId: string | null) {
  return useQuery({
    queryKey: analysisKeys.packet(analysisId ?? "none", packetId ?? "none"),
    queryFn: ({ signal }) =>
      analysisService.packet(analysisId as string, packetId as string, signal),
    enabled: Boolean(analysisId && packetId),
    staleTime: Number.POSITIVE_INFINITY,
  });
}

/** Queues an inspection run for a stored capture. */
export function useStartInspection() {
  const queryClient = useQueryClient();
  return useMutation<Awaited<ReturnType<typeof analysisService.start>>, ApiError, string>({
    mutationFn: (fileId) => analysisService.start(fileId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: analysisKeys.all });
      void queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });
}
