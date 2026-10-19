import { api } from "./apiClient";
import type {
  AuditOverview,
  BoardBriefing,
  ExecutiveAnalyticsData,
  ExecutiveOverviewData,
  FrameworkAssessment,
  FrameworkId,
  ReportBuilderRequest,
  ReportSummary,
  ScheduledReport,
} from "@/types/governance";

/**
 * Executive Reporting & Compliance endpoints.
 *
 * The Spring Boot backend does not implement these yet. Contracts are frozen so
 * every governance surface flips from "Awaiting Live Security Data" to real
 * figures by setting `GOVERNANCE_BACKEND_READY` to true — no component edits.
 */
export const GOVERNANCE_ENDPOINTS = {
  executiveOverview: "/v1/governance/executive/overview",
  executiveAnalytics: "/v1/governance/executive/analytics",
  boardBriefing: "/v1/governance/executive/board",
  reports: "/v1/governance/reports",
  reportById: (id: string) => `/v1/governance/reports/${id}`,
  generateReport: "/v1/governance/reports/generate",
  exportReport: (id: string) => `/v1/governance/reports/${id}/export`,
  schedules: "/v1/governance/reports/schedules",
  scheduleById: (id: string) => `/v1/governance/reports/schedules/${id}`,
  compliance: "/v1/governance/compliance",
  complianceFramework: (framework: FrameworkId) => `/v1/governance/compliance/${framework}`,
  risk: "/v1/governance/risk",
  riskRegister: "/v1/governance/risk/register",
  audit: "/v1/governance/audit",
  auditEvidence: "/v1/governance/audit/evidence",
  exportJobs: "/v1/governance/exports",
} as const;

/** Flip to `true` when the governance endpoints ship. */
export const GOVERNANCE_BACKEND_READY = false;

/** Flip to `true` when document rendering / delivery is implemented. */
export const GOVERNANCE_EXPORT_READY = false;

export const governanceService = {
  getExecutiveOverview: (signal?: AbortSignal) =>
    api.get<ExecutiveOverviewData>(GOVERNANCE_ENDPOINTS.executiveOverview, { signal }),

  getExecutiveAnalytics: (signal?: AbortSignal) =>
    api.get<ExecutiveAnalyticsData>(GOVERNANCE_ENDPOINTS.executiveAnalytics, { signal }),

  getBoardBriefing: (signal?: AbortSignal) =>
    api.get<BoardBriefing>(GOVERNANCE_ENDPOINTS.boardBriefing, { signal }),

  listReports: (signal?: AbortSignal) =>
    api.get<ReportSummary[]>(GOVERNANCE_ENDPOINTS.reports, { signal }),

  getReport: (id: string, signal?: AbortSignal) =>
    api.get<ReportSummary>(GOVERNANCE_ENDPOINTS.reportById(id), { signal }),

  generateReport: (request: ReportBuilderRequest) =>
    api.post<ReportSummary>(GOVERNANCE_ENDPOINTS.generateReport, request),

  listSchedules: (signal?: AbortSignal) =>
    api.get<ScheduledReport[]>(GOVERNANCE_ENDPOINTS.schedules, { signal }),

  listComplianceAssessments: (signal?: AbortSignal) =>
    api.get<FrameworkAssessment[]>(GOVERNANCE_ENDPOINTS.compliance, { signal }),

  getFrameworkAssessment: (framework: FrameworkId, signal?: AbortSignal) =>
    api.get<FrameworkAssessment>(GOVERNANCE_ENDPOINTS.complianceFramework(framework), { signal }),

  getRiskOverview: (signal?: AbortSignal) =>
    api.get<import("@/types/governance").RiskOverview>(GOVERNANCE_ENDPOINTS.risk, { signal }),

  getAuditOverview: (signal?: AbortSignal) =>
    api.get<AuditOverview>(GOVERNANCE_ENDPOINTS.audit, { signal }),
};
