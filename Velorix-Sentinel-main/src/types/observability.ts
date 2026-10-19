/**
 * Platform Observability & DevSecOps domain model.
 *
 * These contracts describe what the monitoring backend (Prometheus /
 * OpenTelemetry collector exposed through the Spring Boot actuator layer) will
 * return. Nothing here is populated with sample telemetry — every surface
 * renders "Awaiting Live Platform Monitoring" until the endpoints ship.
 */

export type HealthState = "OPERATIONAL" | "DEGRADED" | "OUTAGE" | "MAINTENANCE" | "UNKNOWN";

export type MetricUnit =
  "PERCENT" | "BYTES" | "BYTES_PER_SEC" | "REQ_PER_SEC" | "MILLISECONDS" | "COUNT";

export type LogSeverity = "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR" | "FATAL";

export type LogChannel =
  "APPLICATION" | "AUDIT" | "SECURITY" | "AUTHENTICATION" | "API" | "SYSTEM" | "PACKET";

export type EnvironmentName = "DEVELOPMENT" | "TESTING" | "STAGING" | "PRODUCTION";

export type PipelineState = "PASSING" | "FAILING" | "RUNNING" | "NOT_CONFIGURED";

/** One monitored platform component on the Operations Dashboard. */
export interface ServiceHealth {
  id: string;
  name: string;
  description: string;
  state: HealthState;
  /** Uptime percentage over the reporting window; null until monitored. */
  uptime: number | null;
  latencyMs: number | null;
  lastCheckedAt: string | null;
  incidentCount: number | null;
}

export interface PlatformHealthOverview {
  state: HealthState;
  availability: number | null;
  monitoredServices: number | null;
  openIncidents: number | null;
  lastEvaluatedAt: string | null;
  services: ServiceHealth[];
}

/** A single time-series metric descriptor plus its latest sample. */
export interface PlatformMetric {
  id: string;
  name: string;
  description: string;
  unit: MetricUnit;
  /** Latest scraped value; null until the collector is wired. */
  value: number | null;
  /** Warning / critical thresholds used by the alerting engine. */
  warnThreshold: number | null;
  critThreshold: number | null;
  series: MetricSample[] | null;
}

export interface MetricSample {
  timestamp: string;
  value: number;
}

export interface LogEntry {
  id: string;
  timestamp: string;
  severity: LogSeverity;
  channel: LogChannel;
  logger: string;
  message: string;
  traceId?: string | null;
  actor?: string | null;
  context?: Record<string, string | number | boolean | null>;
}

export interface LogQuery {
  channel: LogChannel;
  search?: string;
  severities?: LogSeverity[];
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface LogPage {
  content: LogEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiEndpointMetric {
  id: string;
  method: string;
  path: string;
  module: string;
  requestCount: number | null;
  successRate: number | null;
  failureRate: number | null;
  avgLatencyMs: number | null;
  p95LatencyMs: number | null;
  state: HealthState;
  rateLimit: string | null;
}

export interface DatabaseHealth {
  state: HealthState;
  engine: string | null;
  version: string | null;
  activeConnections: number | null;
  maxConnections: number | null;
  avgQueryMs: number | null;
  slowQueries: number | null;
  sizeBytes: number | null;
  storageUsedPercent: number | null;
  replicationState: HealthState;
  replicationLagSeconds: number | null;
  lastBackupAt: string | null;
}

export interface DeploymentRecord {
  id: string;
  version: string;
  buildNumber: string;
  environment: EnvironmentName;
  region: string | null;
  state: "SUCCEEDED" | "FAILED" | "IN_PROGRESS" | "ROLLED_BACK";
  commitSha: string | null;
  deployedBy: string | null;
  deployedAt: string;
  releaseNotes: string | null;
}

export interface EnvironmentStatus {
  name: EnvironmentName;
  label: string;
  purpose: string;
  state: HealthState;
  version: string | null;
  region: string | null;
  lastDeployedAt: string | null;
  apiBaseUrl: string | null;
}

export interface BackupStatus {
  lastBackupAt: string | null;
  lastBackupSizeBytes: number | null;
  schedule: string | null;
  retentionDays: number | null;
  recoveryPointObjective: string | null;
  recoveryTimeObjective: string | null;
  state: HealthState;
  restoreTestedAt: string | null;
}

export interface AlertRule {
  id: string;
  name: string;
  category: "PLATFORM" | "SECURITY" | "API" | "DATABASE";
  condition: string;
  channels: Array<"EMAIL" | "SLACK" | "WEBHOOK" | "TEAMS">;
  enabled: boolean;
}

export interface ServiceDependencyNode {
  id: string;
  label: string;
  layer: "EDGE" | "APPLICATION" | "PLATFORM" | "DATA" | "EXTERNAL";
  state: HealthState;
  detail: string;
}

export interface ServiceDependencyEdge {
  from: string;
  to: string;
  protocol: string;
  critical: boolean;
}

export interface ServiceDependencyGraph {
  nodes: ServiceDependencyNode[];
  edges: ServiceDependencyEdge[];
}

export interface ObservabilitySettings {
  metricCollectionEnabled: boolean;
  scrapeIntervalSeconds: number;
  logRetentionDays: number;
  auditRetentionDays: number;
  tracingEnabled: boolean;
  tracingSampleRate: number;
  cpuWarnPercent: number;
  memoryWarnPercent: number;
  latencyWarnMs: number;
  errorRateWarnPercent: number;
  notifyEmail: boolean;
  notifySlack: boolean;
  notifyWebhook: boolean;
}

export interface DevSecOpsCheck {
  id: string;
  name: string;
  category: "PIPELINE" | "SAST" | "QUALITY" | "DEPENDENCY" | "CONTAINER" | "SECRETS" | "IAC";
  provider: string;
  state: PipelineState;
  findings: number | null;
  lastRunAt: string | null;
  detail: string;
}
