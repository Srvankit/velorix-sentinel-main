/**
 * Executive Reporting, Compliance, Risk and Audit contracts.
 *
 * These mirror the payloads the Spring Boot backend will expose. Nothing in the
 * UI fabricates a score — every metric below is optional/nullable so panels can
 * render "Awaiting Live Security Data" until the governance APIs ship.
 */

export type PostureBand = "STRONG" | "MODERATE" | "WEAK" | "UNKNOWN";

export type TrendDirection = "UP" | "DOWN" | "FLAT" | "UNKNOWN";

export interface ExecutiveMetric {
  key: string;
  label: string;
  value: number | null;
  unit: "SCORE" | "PERCENT" | "COUNT" | "DAYS" | "CURRENCY" | null;
  delta: number | null;
  trend: TrendDirection;
  band: PostureBand;
  updatedAt: string | null;
}

export interface ExecutiveOverviewData {
  postureScore: number | null;
  riskScore: number | null;
  complianceScore: number | null;
  criticalAssets: number | null;
  openIncidents: number | null;
  resolvedIncidents: number | null;
  platformHealth: PostureBand;
  summary: string | null;
  metrics: ExecutiveMetric[];
  generatedAt: string | null;
}

export interface TrendPoint {
  timestamp: string;
  value: number;
}

export interface CategoryPoint {
  label: string;
  value: number;
}

export interface ExecutiveAnalyticsData {
  incidentTrend: TrendPoint[];
  riskEvolution: TrendPoint[];
  complianceProgress: TrendPoint[];
  departmentComparison: CategoryPoint[];
  assetRiskDistribution: CategoryPoint[];
}

/* ------------------------------------------------------------------ reports */

export type ReportCategory =
  | "EXECUTIVE_SUMMARY"
  | "INCIDENT_SUMMARY"
  | "THREAT_INTELLIGENCE"
  | "COMPLIANCE"
  | "RISK_ASSESSMENT"
  | "ASSET_SECURITY"
  | "SYSTEM_HEALTH"
  | "AUDIT";

export type ReportStatus = "DRAFT" | "GENERATING" | "READY" | "FAILED" | "SCHEDULED";

export interface ReportSummary {
  id: string;
  title: string;
  category: ReportCategory;
  status: ReportStatus;
  period: string | null;
  owner: string | null;
  generatedAt: string | null;
  recommendations: string[];
  overview: string | null;
}

export type ExportFormat = "PDF" | "EXCEL" | "CSV" | "POWERPOINT" | "EMAIL" | "CLOUD";

export interface ReportBuilderRequest {
  title: string;
  from: string | null;
  to: string | null;
  departments: string[];
  incidentCategories: string[];
  riskLevels: string[];
  frameworks: string[];
  sections: string[];
  format: ExportFormat;
}

export type ScheduleCadence = "DAILY" | "WEEKLY" | "MONTHLY" | "QUARTERLY";

export type DeliveryMethod = "EMAIL" | "CLOUD_STORAGE" | "IN_APP" | "WEBHOOK";

export interface ScheduledReport {
  id: string;
  title: string;
  category: ReportCategory;
  cadence: ScheduleCadence;
  delivery: DeliveryMethod;
  recipients: string[];
  nextRunAt: string | null;
  lastRunAt: string | null;
  enabled: boolean;
}

/* --------------------------------------------------------------- compliance */

export type FrameworkId =
  "ISO_27001" | "SOC_2" | "NIST_CSF" | "PCI_DSS" | "CIS_CONTROLS" | "GDPR" | "HIPAA";

export type ControlState = "MET" | "PARTIAL" | "GAP" | "NOT_ASSESSED";

export interface ControlAssessment {
  controlId: string;
  state: ControlState;
  evidenceCount: number;
  owner: string | null;
  assessedAt: string | null;
  note: string | null;
}

export interface FrameworkAssessment {
  framework: FrameworkId;
  coveragePercent: number | null;
  metControls: number | null;
  partialControls: number | null;
  gapControls: number | null;
  totalControls: number | null;
  lastAssessedAt: string | null;
  controls: ControlAssessment[];
  recommendations: string[];
}

/* --------------------------------------------------------------------- risk */

export type RiskLikelihood = "RARE" | "UNLIKELY" | "POSSIBLE" | "LIKELY" | "ALMOST_CERTAIN";

export type RiskImpact = "NEGLIGIBLE" | "MINOR" | "MODERATE" | "MAJOR" | "SEVERE";

export type RiskTreatment = "MITIGATE" | "ACCEPT" | "TRANSFER" | "AVOID";

export interface RiskEntry {
  id: string;
  reference: string;
  title: string;
  category: string;
  owner: string | null;
  likelihood: RiskLikelihood;
  impact: RiskImpact;
  inherentScore: number | null;
  residualScore: number | null;
  treatment: RiskTreatment;
  assetName: string | null;
  reviewDueAt: string | null;
}

export interface RiskOverview {
  register: RiskEntry[];
  categories: CategoryPoint[];
  assetRisk: CategoryPoint[];
  residualAverage: number | null;
  criticalAssets: number | null;
}

/* -------------------------------------------------------------------- audit */

export type FindingSeverity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";

export type FindingStatus = "OPEN" | "IN_REMEDIATION" | "PENDING_REVIEW" | "RESOLVED";

export interface AuditFinding {
  id: string;
  reference: string;
  title: string;
  framework: FrameworkId | null;
  severity: FindingSeverity;
  status: FindingStatus;
  owner: string | null;
  dueAt: string | null;
}

export interface AuditEvent {
  id: string;
  label: string;
  detail: string | null;
  actor: string | null;
  occurredAt: string;
}

export interface AuditOverview {
  timeline: AuditEvent[];
  findings: AuditFinding[];
  evidenceCollected: number | null;
  evidenceRequired: number | null;
  pendingReviews: number | null;
  compliancePercent: number | null;
}

/* ---------------------------------------------------------- board reporting */

export interface BoardBriefing {
  businessRisk: PostureBand;
  maturityLevel: number | null;
  maturityLabel: string | null;
  investmentUtilisation: number | null;
  complianceReadiness: number | null;
  operationalStatus: PostureBand;
  recommendations: string[];
  notes: string | null;
  preparedAt: string | null;
}
