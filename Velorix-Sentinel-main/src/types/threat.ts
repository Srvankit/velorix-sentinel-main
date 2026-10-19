/** Threat detection & packet analysis domain types. */

export type Severity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";

export type CaptureStatus = "QUEUED" | "PROCESSING" | "ANALYZED" | "FAILED";

export type TransportProtocol = "TCP" | "UDP" | "ICMP" | "ARP" | "OTHER";

export interface CaptureFile {
  id: string;
  fileName: string;
  sizeBytes: number;
  status: CaptureStatus;
  packetCount: number;
  threatCount: number;
  riskScore: number;
  uploadedAt: string;
  analyzedAt: string | null;
}

export interface Threat {
  id: string;
  captureId: string;
  name: string;
  category: string;
  severity: Severity;
  sourceIp: string;
  destinationIp: string;
  protocol: TransportProtocol;
  confidence: number;
  detectedAt: string;
  description: string;
  mitreTechnique: string | null;
}

export interface Packet {
  id: string;
  captureId: string;
  index: number;
  timestamp: string;
  sourceIp: string;
  sourcePort: number | null;
  destinationIp: string;
  destinationPort: number | null;
  protocol: TransportProtocol;
  lengthBytes: number;
  flags: string[];
  payloadPreview: string | null;
}

export interface AiInsight {
  summary: string;
  riskScore: number;
  recommendations: string[];
  iocs: string[];
  cves: string[];
  generatedAt: string;
}
