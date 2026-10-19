/**
 * Incident Response & Case Management contracts.
 *
 * These types mirror the payloads the Spring Boot backend will expose under
 * `/api/v1/incidents`. They are frozen ahead of the backend so the UI can be
 * switched from "awaiting" states to live data without refactoring.
 */

export type IncidentSeverity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFORMATIONAL";

export type IncidentStatus =
  | "NEW"
  | "TRIAGED"
  | "INVESTIGATING"
  | "CONTAINED"
  | "ERADICATED"
  | "RECOVERED"
  | "RESOLVED"
  | "CLOSED"
  | "FALSE_POSITIVE";

export type IncidentPriority = "P1" | "P2" | "P3" | "P4";

export type IncidentCategory =
  | "MALWARE"
  | "RANSOMWARE"
  | "PHISHING"
  | "CREDENTIAL_THEFT"
  | "DATA_EXFILTRATION"
  | "INSIDER_THREAT"
  | "NETWORK_INTRUSION"
  | "DENIAL_OF_SERVICE"
  | "POLICY_VIOLATION"
  | "UNKNOWN";

export type IncidentSource =
  "PACKET_ANALYSIS" | "THREAT_DETECTION_ENGINE" | "THREAT_INTELLIGENCE" | "MANUAL" | "INTEGRATION";

export interface AnalystRef {
  id: string;
  displayName: string;
  email: string;
  role: string;
}

export interface Incident {
  id: string;
  reference: string;
  title: string;
  description: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  priority: IncidentPriority;
  category: IncidentCategory;
  source: IncidentSource;
  riskScore: number;
  assignee: AnalystRef | null;
  watchers: AnalystRef[];
  tags: string[];
  createdAt: string;
  updatedAt: string;
  detectedAt: string | null;
  respondedAt: string | null;
  resolvedAt: string | null;
  alertCount: number;
  evidenceCount: number;
  affectedAssetCount: number;
}

export interface IncidentMetrics {
  open: number;
  critical: number;
  investigating: number;
  resolved: number;
  /** Mean time to detect, in minutes. */
  mttdMinutes: number | null;
  /** Mean time to respond, in minutes. */
  mttrMinutes: number | null;
  bySeverity: Record<IncidentSeverity, number>;
  byStatus: Record<IncidentStatus, number>;
  queueDepth: number;
}

export type IncidentEventKind =
  | "CASE_CREATED"
  | "ANALYST_ASSIGNED"
  | "STATUS_CHANGED"
  | "SEVERITY_CHANGED"
  | "EVIDENCE_ADDED"
  | "COMMENT_ADDED"
  | "PLAYBOOK_EXECUTED"
  | "CONTAINMENT_ACTION"
  | "RESOLUTION";

export interface IncidentEvent {
  id: string;
  kind: IncidentEventKind;
  summary: string;
  detail: string | null;
  actor: AnalystRef | null;
  occurredAt: string;
}

export type EvidenceKind =
  "LOG" | "SCREENSHOT" | "NETWORK_CAPTURE" | "INDICATOR" | "REPORT" | "ATTACHMENT";

export interface CustodyEntry {
  id: string;
  action: string;
  actor: AnalystRef | null;
  occurredAt: string;
  hash: string | null;
}

export interface EvidenceItem {
  id: string;
  incidentId: string;
  name: string;
  kind: EvidenceKind;
  sizeBytes: number | null;
  sha256: string | null;
  collectedAt: string;
  collectedBy: AnalystRef | null;
  custody: CustodyEntry[];
}

export interface AffectedAsset {
  id: string;
  hostname: string | null;
  ipAddress: string | null;
  macAddress: string | null;
  assetType: string;
  criticality: IncidentSeverity;
  firstSeen: string;
  lastSeen: string;
}

export interface RelatedAlert {
  id: string;
  title: string;
  severity: IncidentSeverity;
  source: string;
  raisedAt: string;
}

export interface IncidentIndicator {
  id: string;
  kind: string;
  value: string;
  severity: IncidentSeverity;
  firstSeen: string;
}

export interface MitreMapping {
  tacticId: string;
  tacticName: string;
  techniqueId: string;
  techniqueName: string;
  confidence: number;
}

export interface IncidentComment {
  id: string;
  incidentId: string;
  body: string;
  author: AnalystRef | null;
  mentions: string[];
  internal: boolean;
  createdAt: string;
}

export interface IncidentDetail extends Incident {
  timeline: IncidentEvent[];
  evidence: EvidenceItem[];
  assets: AffectedAsset[];
  indicators: IncidentIndicator[];
  relatedAlerts: RelatedAlert[];
  mitre: MitreMapping[];
  recommendations: string[];
  aiSummary: string | null;
  comments: IncidentComment[];
}

export interface IncidentQuery {
  page?: number;
  size?: number;
  search?: string;
  severity?: IncidentSeverity[];
  status?: IncidentStatus[];
  priority?: IncidentPriority[];
  category?: IncidentCategory[];
  assigneeId?: string;
  sort?: string;
}

export interface CreateCaseRequest {
  title: string;
  description: string;
  severity: IncidentSeverity;
  priority: IncidentPriority;
  category: IncidentCategory;
  source: IncidentSource;
  tags: string[];
  assigneeId?: string;
}

export interface IncidentReport {
  id: string;
  incidentId: string;
  title: string;
  generatedAt: string;
  executiveSummary: string;
  technicalDetails: string;
  recommendations: string[];
  lessonsLearned: string[];
}
