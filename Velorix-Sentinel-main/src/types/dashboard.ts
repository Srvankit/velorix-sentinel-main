/** Dashboard read models returned by `/api/v1/dashboard/*`. */

import type { Severity } from "./threat";

export interface DashboardSummary {
  securityScore: number;
  totalAnalyses: number;
  completedAnalyses: number;
  runningAnalyses: number;
  threatsDetected: number;
  criticalThreats: number;
  highThreats: number;
  filesUploaded: number;
  activeInvestigations: number;
  packetsInspected: number;
}

export interface TrendPoint {
  bucketStart: string;
  total: number;
  critical: number;
  high: number;
  medium: number;
  low: number;
}

export interface NamedCount {
  label: string;
  count: number;
}

export interface VolumePoint {
  bucketStart: string;
  packets: number;
}

export interface DashboardCharts {
  threatTrend: TrendPoint[];
  protocolDistribution: NamedCount[];
  severityBreakdown: NamedCount[];
  packetVolume: VolumePoint[];
  topSourceIps: NamedCount[];
  topDestinationIps: NamedCount[];
}

export type AnalysisStatus = "UPLOADED" | "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";

export interface RecentAnalysis {
  id: string;
  uploadedFileId: string;
  originalFileName: string;
  status: AnalysisStatus;
  stage: string;
  progressPercent: number;
  totalPackets: number;
  riskScore: number;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export type ThreatStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "FALSE_POSITIVE";

export interface ThreatRecord {
  id: string;
  analysisId: string;
  threatType: string;
  severity: Severity;
  status: ThreatStatus;
  confidenceScore: number;
  confidencePercent: number;
  title: string;
  description: string;
  sourceIp: string | null;
  destinationIp: string | null;
  protocol: string;
  detectionRule: string;
  mitreTechnique: string | null;
  detectedAt: string;
}

export type ActivityType = string;

export interface ActivityEntry {
  id: string;
  activityType: ActivityType;
  description: string | null;
  ipAddress: string | null;
  successful: boolean;
  occurredAt: string;
}

export type ComponentStatus = "UP" | "DEGRADED" | "DOWN";

export interface ComponentHealth {
  name: string;
  status: ComponentStatus;
  detail: string;
}

export interface SystemHealth {
  api: ComponentHealth;
  database: ComponentHealth;
  worker: ComponentHealth;
  checkedAt: string;
}
