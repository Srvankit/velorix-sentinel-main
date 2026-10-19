import { api } from "./apiClient";
import type {
  AccountPreferences,
  AdminAuditEntry,
  AdminOverview,
  AdminUserPage,
  ApiKeySummary,
  BillingOverview,
  BusinessUnit,
  IdentityProvider,
  Integration,
  MfaOverview,
  Organization,
  RoleDefinition,
  SecurityPolicy,
  SystemConfiguration,
  UserSession,
  WebhookSubscription,
  Workspace,
} from "@/types/admin";

/**
 * Enterprise Administration Center endpoints.
 *
 * The Spring Boot backend does not implement these yet. The contracts are
 * frozen here so every administration surface flips from "Awaiting Live Data"
 * to real tenant data by setting `ADMIN_BACKEND_READY` to true — with no
 * component changes required.
 *
 * NOTE: nothing in this module touches the Upload, Packet Analysis or
 * Authentication APIs, which remain owned by their existing services.
 */
export const ADMIN_ENDPOINTS = {
  overview: "/v1/admin/overview",
  activity: "/v1/admin/activity",

  organizations: "/v1/admin/organizations",
  organizationById: (id: string) => `/v1/admin/organizations/${id}`,
  businessUnits: (organizationId: string) =>
    `/v1/admin/organizations/${organizationId}/business-units`,

  users: "/v1/admin/users",
  userById: (id: string) => `/v1/admin/users/${id}`,
  bulkUsers: "/v1/admin/users/bulk",

  roles: "/v1/admin/roles",
  roleById: (id: string) => `/v1/admin/roles/${id}`,

  workspaces: "/v1/admin/workspaces",
  workspaceById: (id: string) => `/v1/admin/workspaces/${id}`,

  policies: "/v1/admin/policies",
  policyById: (id: string) => `/v1/admin/policies/${id}`,

  integrations: "/v1/admin/integrations",
  apiKeys: "/v1/admin/integrations/api-keys",
  webhooks: "/v1/admin/integrations/webhooks",

  auditLogs: "/v1/admin/audit-logs",
  auditExport: "/v1/admin/audit-logs/export",

  sessions: "/v1/admin/sessions",
  revokeSession: (id: string) => `/v1/admin/sessions/${id}/revoke`,

  billing: "/v1/admin/billing",
  invoices: "/v1/admin/billing/invoices",

  mfa: "/v1/admin/security/mfa",
  identityProviders: "/v1/admin/security/identity-providers",

  systemConfiguration: "/v1/admin/system/configuration",
  accountPreferences: "/v1/admin/account/preferences",
} as const;

/** Flip to `true` when the administration endpoints ship. */
export const ADMIN_BACKEND_READY = false;

/** Flip to `true` once admin mutations (create/suspend/revoke) are implemented. */
export const ADMIN_WRITE_READY = false;

/** Flip to `true` when subscription & billing is provisioned. */
export const ADMIN_BILLING_READY = false;

export const adminService = {
  getOverview: (signal?: AbortSignal) =>
    api.get<AdminOverview>(ADMIN_ENDPOINTS.overview, { signal }),

  listActivity: (signal?: AbortSignal) =>
    api.get<AdminAuditEntry[]>(ADMIN_ENDPOINTS.activity, { signal }),

  listOrganizations: (signal?: AbortSignal) =>
    api.get<Organization[]>(ADMIN_ENDPOINTS.organizations, { signal }),

  getOrganization: (id: string, signal?: AbortSignal) =>
    api.get<Organization>(ADMIN_ENDPOINTS.organizationById(id), { signal }),

  listBusinessUnits: (organizationId: string, signal?: AbortSignal) =>
    api.get<BusinessUnit[]>(ADMIN_ENDPOINTS.businessUnits(organizationId), { signal }),

  listUsers: (params?: { page?: number; size?: number; search?: string }, signal?: AbortSignal) =>
    api.get<AdminUserPage>(ADMIN_ENDPOINTS.users, { params, signal }),

  listRoles: (signal?: AbortSignal) => api.get<RoleDefinition[]>(ADMIN_ENDPOINTS.roles, { signal }),

  listWorkspaces: (signal?: AbortSignal) =>
    api.get<Workspace[]>(ADMIN_ENDPOINTS.workspaces, { signal }),

  listPolicies: (signal?: AbortSignal) =>
    api.get<SecurityPolicy[]>(ADMIN_ENDPOINTS.policies, { signal }),

  listIntegrations: (signal?: AbortSignal) =>
    api.get<Integration[]>(ADMIN_ENDPOINTS.integrations, { signal }),

  listApiKeys: (signal?: AbortSignal) =>
    api.get<ApiKeySummary[]>(ADMIN_ENDPOINTS.apiKeys, { signal }),

  listWebhooks: (signal?: AbortSignal) =>
    api.get<WebhookSubscription[]>(ADMIN_ENDPOINTS.webhooks, { signal }),

  listAuditLogs: (params?: { search?: string; from?: string; to?: string }, signal?: AbortSignal) =>
    api.get<AdminAuditEntry[]>(ADMIN_ENDPOINTS.auditLogs, { params, signal }),

  listSessions: (signal?: AbortSignal) =>
    api.get<UserSession[]>(ADMIN_ENDPOINTS.sessions, { signal }),

  getBilling: (signal?: AbortSignal) =>
    api.get<BillingOverview>(ADMIN_ENDPOINTS.billing, { signal }),

  getMfaOverview: (signal?: AbortSignal) => api.get<MfaOverview>(ADMIN_ENDPOINTS.mfa, { signal }),

  listIdentityProviders: (signal?: AbortSignal) =>
    api.get<IdentityProvider[]>(ADMIN_ENDPOINTS.identityProviders, { signal }),

  getSystemConfiguration: (signal?: AbortSignal) =>
    api.get<SystemConfiguration>(ADMIN_ENDPOINTS.systemConfiguration, { signal }),

  getAccountPreferences: (signal?: AbortSignal) =>
    api.get<AccountPreferences>(ADMIN_ENDPOINTS.accountPreferences, { signal }),
};
