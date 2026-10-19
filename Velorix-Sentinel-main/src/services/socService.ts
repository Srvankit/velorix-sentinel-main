import { api } from "./apiClient";
import type { PageResponse } from "@/types/api";
import type {
  AlertQuery,
  AnalystProductivity,
  ConsoleLogEntry,
  SocAlert,
  SocNotification,
  SystemComponentStatus,
} from "@/types/soc";

/**
 * SOC Analyst Workspace endpoints.
 *
 * None of these exist on the Spring Boot backend yet. The contracts are frozen
 * so every workspace surface flips from "awaiting telemetry" to live data by
 * setting `SOC_BACKEND_READY` to true — no component changes required.
 */
export const SOC_ENDPOINTS = {
  alerts: "/v1/soc/alerts",
  alertById: (id: string) => `/v1/soc/alerts/${id}`,
  alertStream: "/v1/soc/alerts/stream",
  triageQueue: "/v1/soc/alerts/queue",
  bulkTriage: "/v1/soc/alerts/bulk",
  systemStatus: "/v1/soc/status",
  productivity: "/v1/soc/productivity",
  notifications: "/v1/soc/notifications",
  notificationRead: (id: string) => `/v1/soc/notifications/${id}/read`,
  notificationReadAll: "/v1/soc/notifications/read-all",
  console: "/v1/soc/console",
} as const;

/**
 * WebSocket topics the workspace will subscribe to once the realtime gateway
 * ships. Documented here so the backend and frontend agree on channel names.
 */
export const SOC_WS_TOPICS = {
  alerts: "/topic/soc/alerts",
  status: "/topic/soc/status",
  console: "/topic/soc/console",
  notifications: "/user/queue/notifications",
  inspection: "/topic/soc/inspection",
} as const;

/** Flip to `true` when the SOC endpoints ship. */
export const SOC_BACKEND_READY = false;

/** Flip to `true` when the realtime gateway ships. */
export const SOC_STREAM_READY = false;

export const socService = {
  listAlerts: (query: AlertQuery = {}, signal?: AbortSignal) =>
    api.get<PageResponse<SocAlert>>(SOC_ENDPOINTS.alerts, { params: query, signal }),

  getAlert: (id: string, signal?: AbortSignal) =>
    api.get<SocAlert>(SOC_ENDPOINTS.alertById(id), { signal }),

  getTriageQueue: (signal?: AbortSignal) =>
    api.get<SocAlert[]>(SOC_ENDPOINTS.triageQueue, { signal }),

  getSystemStatus: (signal?: AbortSignal) =>
    api.get<SystemComponentStatus[]>(SOC_ENDPOINTS.systemStatus, { signal }),

  getProductivity: (signal?: AbortSignal) =>
    api.get<AnalystProductivity>(SOC_ENDPOINTS.productivity, { signal }),

  listNotifications: (signal?: AbortSignal) =>
    api.get<SocNotification[]>(SOC_ENDPOINTS.notifications, { signal }),

  markNotificationRead: (id: string) =>
    api.patch<SocNotification>(SOC_ENDPOINTS.notificationRead(id), {}),

  markAllNotificationsRead: () => api.patch<void>(SOC_ENDPOINTS.notificationReadAll, {}),

  getConsoleLog: (signal?: AbortSignal) =>
    api.get<ConsoleLogEntry[]>(SOC_ENDPOINTS.console, { signal }),
};
