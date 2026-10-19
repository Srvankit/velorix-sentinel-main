/**
 * Read models for the detection engine, mirroring `/api/v1/threats/*`.
 *
 * These mirror the Spring Boot DTOs exactly — no field is invented on the client.
 */

export type ThreatSeverity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";

export type ThreatStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "FALSE_POSITIVE";

export type OverallStatus = "SECURE" | "LOW_RISK" | "ELEVATED" | "HIGH_RISK" | "CRITICAL";

export type ThreatType =
  | "PORT_SCAN"
  | "SYN_SCAN"
  | "UDP_SCAN"
  | "DDOS"
  | "ICMP_FLOOD"
  | "DNS_ANOMALY"
  | "BRUTE_FORCE"
  | "REPEATED_CONNECTION"
  | "DATA_EXFILTRATION"
  | "LARGE_PAYLOAD"
  | "BEACONING"
  | "SUSPICIOUS_EXTERNAL_COMM"
  | "MALWARE"
  | "C2_COMMUNICATION"
  | "SUSPICIOUS_TRAFFIC"
  | "UNKNOWN_PROTOCOL"
  | "ABNORMAL_PACKET_SIZE"
  | "UNKNOWN";

export const THREAT_SEVERITIES: ThreatSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

export const THREAT_STATUSES: ThreatStatus[] = [
  "OPEN",
  "ACKNOWLEDGED",
  "RESOLVED",
  "FALSE_POSITIVE",
];

/** A single finding raised by the detection engine. */
export interface Threat {
  id: string;
  analysisId: string;
  threatType: ThreatType;
  severity: ThreatSeverity;
  status: ThreatStatus;
  confidenceScore: number;
  confidencePercent: number;
  title: string;
  description: string | null;
  recommendation: string | null;
  evidence: string | null;
  detectionRule: string | null;
  ruleVersion: string | null;
  mitreTechnique: string | null;
  mitreTechniqueName: string | null;
  sourceIp: string | null;
  destinationIp: string | null;
  protocol: string | null;
  packetCount: number;
  samplePacketNumbers: number[] | null;
  firstSeenAt: string | null;
  lastSeenAt: string | null;
  detectedAt: string | null;
  createdAt: string | null;
}

export interface ThreatHostCount {
  ip: string;
  count: number;
}

export interface ThreatTimelineBucket {
  bucketStart: string;
  total: number;
  critical: number;
  high: number;
  medium: number;
  low: number;
}

export interface ThreatStats {
  totalThreats: number;
  riskScore: number;
  overallStatus: OverallStatus | null;
  severityCounts: Partial<Record<ThreatSeverity, number>> | null;
  categoryCounts: Partial<Record<ThreatType, number>> | null;
  topSources: ThreatHostCount[] | null;
  topDestinations: ThreatHostCount[] | null;
  timeline: ThreatTimelineBucket[] | null;
}

export interface DetectionRule {
  id: string;
  name: string;
  version: string | null;
  description: string | null;
  threatType: ThreatType;
  baseSeverity: ThreatSeverity;
  mitreTechnique: string | null;
  mitreTechniqueName: string | null;
  detectionLogic: string | null;
  recommendation: string | null;
  enabled: boolean;
}

export interface ThreatQuery {
  analysisId?: string;
  severity?: ThreatSeverity;
  threatType?: ThreatType;
  status?: ThreatStatus;
  detectionRule?: string;
  search?: string;
  minConfidence?: number;
  page?: number;
  size?: number;
  sort?: string;
  direction?: "ASC" | "DESC";
}
