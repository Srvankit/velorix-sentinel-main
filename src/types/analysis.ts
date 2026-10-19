/** Deep packet inspection read models returned by `/api/v1/analysis/*`. */

import type { UploadedFile } from "./file";

export type AnalysisStatus = "UPLOADED" | "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";

export type AnalysisStage =
  "PREPARING" | "READING" | "EXTRACTING" | "SAVING" | "COMPLETED" | "FAILED";

export type Protocol =
  "TCP" | "UDP" | "ICMP" | "ICMPV6" | "ARP" | "DNS" | "DHCP" | "HTTP" | "HTTPS" | "TLS" | "OTHER";

export type NetworkProtocol = "IPV4" | "IPV6" | "ARP" | "OTHER";

export const PROTOCOLS: Protocol[] = [
  "TCP",
  "UDP",
  "ICMP",
  "ICMPV6",
  "ARP",
  "DNS",
  "DHCP",
  "HTTP",
  "HTTPS",
  "TLS",
  "OTHER",
];

export const NETWORK_PROTOCOLS: NetworkProtocol[] = ["IPV4", "IPV6", "ARP", "OTHER"];

export interface Analysis {
  id: string;
  uploadedFileId: string;
  originalFileName: string;
  status: AnalysisStatus;
  stage: AnalysisStage;
  progressPercent: number;
  startedAt: string | null;
  completedAt: string | null;
  duration: number | null;
  totalPackets: number;
  processedPackets: number;
  malformedPackets: number;
  truncated: boolean;
  maliciousPackets: number;
  safePackets: number;
  averagePacketSize: number;
  uniqueSourceIps: number;
  uniqueDestinationIps: number;
  captureStartedAt: string | null;
  captureEndedAt: string | null;
  captureDuration: number | null;
  riskScore: number;
  analysisVersion: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AnalysisThreatSummary {
  id: string;
  analysisId: string;
  totalThreats: number;
  criticalThreats: number;
  highThreats: number;
  mediumThreats: number;
  lowThreats: number;
  riskScore: number;
  overallStatus: string;
}

export interface AnalysisDetail {
  analysis: Analysis;
  uploadedFile: UploadedFile;
  summary: AnalysisThreatSummary | null;
}

export interface Packet {
  id: string;
  analysisId: string;
  packetNumber: number;
  timestamp: string;
  sourceIp: string;
  destinationIp: string;
  sourcePort: number | null;
  destinationPort: number | null;
  protocol: Protocol;
  networkProtocol: NetworkProtocol;
  packetLength: number;
  tcpFlags: string | null;
  payloadSize: number;
  info: string | null;
  malformed: boolean;
  suspicious: boolean;
}

export interface PacketDetail extends Packet {
  sourceMac: string | null;
  destinationMac: string | null;
  ttl: number | null;
  sequenceNumber: number | null;
  acknowledgementNumber: number | null;
  windowSize: number | null;
  checksum: number | null;
  capturedLength: number;
}

export interface PacketQuery {
  protocol?: Protocol;
  networkProtocol?: NetworkProtocol;
  suspicious?: boolean;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: "ASC" | "DESC";
}

export interface AnalysisQuery {
  status?: AnalysisStatus;
  uploadedFileId?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: "ASC" | "DESC";
}

/** True while the pipeline is still producing rows for this run. */
export function isRunning(analysis: Pick<Analysis, "status">): boolean {
  return analysis.status === "QUEUED" || analysis.status === "PROCESSING";
}
