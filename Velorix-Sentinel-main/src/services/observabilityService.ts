import { api } from "./apiClient";
import type {
  AlertRule,
  ApiEndpointMetric,
  BackupStatus,
  DatabaseHealth,
  DeploymentRecord,
  DevSecOpsCheck,
  EnvironmentStatus,
  LogPage,
  LogQuery,
  ObservabilitySettings,
  PlatformHealthOverview,
  PlatformMetric,
  ServiceDependencyGraph,
} from "@/types/observability";

/**
 * Platform Observability & DevSecOps endpoints.
 *
 * The Spring Boot backend does not expose these yet. Contracts are frozen here
 * so every observability surface flips from "Awaiting Live Platform Monitoring"
 * to real telemetry by setting `OBSERVABILITY_BACKEND_READY` to true — with no
 * component changes required.
 *
 * Planned providers: Spring Boot Actuator + Micrometer -> Prometheus,
 * OpenTelemetry traces, Loki/Elastic for logs, Grafana dashboards.
 *
 * NOTE: nothing in this module touches Upload, Packet Analysis or
 * Authentication APIs.
 */
export const OBSERVABILITY_ENDPOINTS = {
  health: "/v1/ops/health",
  services: "/v1/ops/health/services",

  metrics: "/v1/ops/metrics",
  metricSeries: (id: string) => `/v1/ops/metrics/${id}/series`,

  logs: "/v1/ops/logs",
  logExport: "/v1/ops/logs/export",

  apiMetrics: "/v1/ops/api/endpoints",
  database: "/v1/ops/database",

  deployments: "/v1/ops/deployments",
  currentDeployment: "/v1/ops/deployments/current",
  rollback: (id: string) => `/v1/ops/deployments/${id}/rollback`,

  environments: "/v1/ops/environments",

  backups: "/v1/ops/backups",
  restore: (id: string) => `/v1/ops/backups/${id}/restore`,

  alertRules: "/v1/ops/alerts/rules",
  dependencies: "/v1/ops/dependencies",

  settings: "/v1/ops/settings",
  devsecops: "/v1/ops/devsecops/checks",
} as const;

/** Flip to `true` when the observability endpoints ship. */
export const OBSERVABILITY_BACKEND_READY = false;

/** Flip to `true` once operational mutations (rollback, restore) are implemented. */
export const OBSERVABILITY_WRITE_READY = false;

/** Flip to `true` once alerting delivery (email/Slack/webhook) is implemented. */
export const OBSERVABILITY_ALERTING_READY = false;

function query(params: Record<string, string | number | undefined | string[]>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === "") return;
    if (Array.isArray(value)) value.forEach((entry) => search.append(key, entry));
    else search.append(key, String(value));
  });
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

export const observabilityService = {
  getHealth: (signal?: AbortSignal) =>
    api.get<PlatformHealthOverview>(OBSERVABILITY_ENDPOINTS.health, { signal }),

  listMetrics: (signal?: AbortSignal) =>
    api.get<PlatformMetric[]>(OBSERVABILITY_ENDPOINTS.metrics, { signal }),

  searchLogs: (params: LogQuery, signal?: AbortSignal) =>
    api.get<LogPage>(
      `${OBSERVABILITY_ENDPOINTS.logs}${query({
        channel: params.channel,
        search: params.search,
        severity: params.severities,
        from: params.from,
        to: params.to,
        page: params.page,
        size: params.size,
      })}`,
      { signal },
    ),

  listApiMetrics: (signal?: AbortSignal) =>
    api.get<ApiEndpointMetric[]>(OBSERVABILITY_ENDPOINTS.apiMetrics, { signal }),

  getDatabaseHealth: (signal?: AbortSignal) =>
    api.get<DatabaseHealth>(OBSERVABILITY_ENDPOINTS.database, { signal }),

  listDeployments: (signal?: AbortSignal) =>
    api.get<DeploymentRecord[]>(OBSERVABILITY_ENDPOINTS.deployments, { signal }),

  listEnvironments: (signal?: AbortSignal) =>
    api.get<EnvironmentStatus[]>(OBSERVABILITY_ENDPOINTS.environments, { signal }),

  getBackupStatus: (signal?: AbortSignal) =>
    api.get<BackupStatus>(OBSERVABILITY_ENDPOINTS.backups, { signal }),

  listAlertRules: (signal?: AbortSignal) =>
    api.get<AlertRule[]>(OBSERVABILITY_ENDPOINTS.alertRules, { signal }),

  getDependencyGraph: (signal?: AbortSignal) =>
    api.get<ServiceDependencyGraph>(OBSERVABILITY_ENDPOINTS.dependencies, { signal }),

  getSettings: (signal?: AbortSignal) =>
    api.get<ObservabilitySettings>(OBSERVABILITY_ENDPOINTS.settings, { signal }),

  listDevSecOpsChecks: (signal?: AbortSignal) =>
    api.get<DevSecOpsCheck[]>(OBSERVABILITY_ENDPOINTS.devsecops, { signal }),
};
