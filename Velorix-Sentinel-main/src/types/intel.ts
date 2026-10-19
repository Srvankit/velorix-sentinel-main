import type { IocKind, IocSeverity } from "@/data/ioc-catalog";

/** Contracts for the (not yet implemented) threat intelligence backend. */

export interface ThreatLandscapeMetric {
  key: string;
  label: string;
  value: number | null;
  unit?: string;
  /** Percentage change against the previous window; null until data exists. */
  delta: number | null;
}

export interface ThreatLandscape {
  generatedAt: string;
  metrics: ThreatLandscapeMetric[];
  categories: Array<{ name: string; count: number }>;
  emergingThreats: Array<{ id: string; name: string; severity: IocSeverity; firstSeen: string }>;
}

export interface IntelSource {
  id: string;
  name: string;
  kind: "commercial" | "open-source" | "government" | "internal";
  status: "connected" | "disconnected" | "error";
  lastSyncedAt: string | null;
  indicatorCount: number | null;
}

export interface CveRecord {
  id: string;
  title: string;
  severity: IocSeverity;
  cvssScore: number;
  publishedAt: string;
  vendor: string;
  affectedProducts: string[];
  exploitAvailable: boolean;
  mitigation: string;
  references: string[];
}

export interface ThreatFeedEvent {
  id: string;
  observedAt: string;
  name: string;
  severity: IocSeverity;
  status: "new" | "triaged" | "contained" | "closed";
  source: string;
  country: string | null;
  category: string;
  riskScore: number;
}

export interface ThreatMapPoint {
  countryCode: string;
  countryName: string;
  latitude: number;
  longitude: number;
  eventCount: number;
  topCategory: string | null;
}

export interface Watchlist {
  id: string;
  name: string;
  description: string;
  kinds: IocKind[];
  itemCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ThreatReport {
  id: string;
  title: string;
  publishedAt: string;
  severity: IocSeverity;
  executiveSummary: string;
  technicalSummary: string;
  indicators: string[];
  timeline: Array<{ at: string; event: string }>;
  recommendations: string[];
}

export interface IntelSearchResult {
  id: string;
  kind: "mitre" | "cve" | "ioc" | "malware" | "report";
  title: string;
  subtitle: string;
  href: string;
}
