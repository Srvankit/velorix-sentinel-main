/**
 * SOC Analyst Workspace contracts.
 *
 * These types mirror the API/WebSocket payloads the Spring Boot backend will
 * emit. Nothing here is populated with sample data — every surface renders an
 * honest empty state until the telemetry channels are connected.
 */

export type AlertSeverity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFORMATIONAL";

export type AlertStatus =
  "NEW" | "TRIAGED" | "IN_PROGRESS" | "ESCALATED" | "SUPPRESSED" | "CLOSED" | "FALSE_POSITIVE";

export type AlertPriority = "P1" | "P2" | "P3" | "P4";

export type AlertCategory =
  "NETWORK" | "MALWARE" | "INTRUSION" | "RECONNAISSANCE" | "EXFILTRATION" | "POLICY" | "ANOMALY";

export interface AnalystRefLite {
  id: string;
  displayName: string;
}

/** A single detection emitted by the threat engine or inspection worker. */
export interface SocAlert {
  id: string;
  reference: string;
  name: string;
  description: string | null;
  severity: AlertSeverity;
  priority: AlertPriority;
  category: AlertCategory;
  status: AlertStatus;
  riskScore: number;
  sourceAddress: string | null;
  sourcePort: number | null;
  destinationAddress: string | null;
  destinationPort: number | null;
  protocol: string | null;
  detector: string | null;
  assignee: AnalystRefLite | null;
  createdAt: string;
  observedAt: string | null;
}

export interface AlertQuery {
  page?: number;
  size?: number;
  search?: string;
  severity?: AlertSeverity[];
  status?: AlertStatus[];
  sort?: string;
}

/** Live status of one platform component, rendered in the system status panel. */
export type ComponentState = "OPERATIONAL" | "DEGRADED" | "OFFLINE" | "UNKNOWN";

export interface SystemComponentStatus {
  key:
    | "BACKEND"
    | "DATABASE"
    | "AUTHENTICATION"
    | "THREAT_ENGINE"
    | "INSPECTION_WORKER"
    | "AI_COPILOT"
    | "THREAT_INTELLIGENCE";
  label: string;
  state: ComponentState;
  detail: string | null;
  latencyMs: number | null;
  checkedAt: string | null;
}

export interface AnalystProductivity {
  assignedCases: number;
  resolvedToday: number;
  averageResolutionMinutes: number | null;
  pendingReviews: number;
  openTasks: number;
  bookmarks: number;
  pinnedInvestigations: number;
}

export type NotificationKind = "MENTION" | "ASSIGNMENT" | "WARNING" | "SYSTEM";

export interface SocNotification {
  id: string;
  kind: NotificationKind;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
  link: string | null;
}

export type ConsoleChannel = "SYSTEM" | "AUDIT" | "SECURITY" | "PACKET";

export type ConsoleLevel = "INFO" | "WARN" | "ERROR" | "DEBUG";

export interface ConsoleLogEntry {
  id: string;
  channel: ConsoleChannel;
  level: ConsoleLevel;
  message: string;
  actor: string | null;
  timestamp: string;
}

/** Connection state of a future WebSocket telemetry channel. */
export type StreamState = "DISABLED" | "CONNECTING" | "OPEN" | "CLOSED" | "ERROR";
