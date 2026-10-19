/**
 * Enterprise Administration Center contracts.
 *
 * These mirror the payloads the Spring Boot backend will expose once the
 * administration APIs ship. Every metric is nullable and every collection
 * defaults to empty so the UI can honestly render "Awaiting Live Data"
 * instead of fabricating tenants, users, sessions or audit records.
 */

export type AdminHealthBand = "HEALTHY" | "DEGRADED" | "CRITICAL" | "UNKNOWN";

export type EntityStatus = "ACTIVE" | "SUSPENDED" | "PENDING" | "ARCHIVED" | "DISABLED";

export type MfaStatus = "ENFORCED" | "ENABLED" | "NOT_ENROLLED" | "EXEMPT" | "UNKNOWN";

/* --------------------------------------------------------------- overview */

export interface AdminMetric {
  key: string;
  label: string;
  value: number | null;
  unit: "COUNT" | "PERCENT" | "BYTES" | "SCORE" | null;
  delta: number | null;
  band: AdminHealthBand;
}

export interface AdminOverview {
  organizations: number | null;
  activeUsers: number | null;
  securityPolicies: number | null;
  workspaces: number | null;
  connectedServices: number | null;
  authenticationHealth: AdminHealthBand;
  platformHealth: AdminHealthBand;
  apiRequests24h: number | null;
  licensesUsed: number | null;
  licensesTotal: number | null;
  metrics: AdminMetric[];
  generatedAt: string | null;
}

/* ---------------------------------------------------------- organizations */

export interface Organization {
  id: string;
  name: string;
  slug: string;
  industry: string | null;
  region: string | null;
  businessUnits: number | null;
  departments: number | null;
  members: number | null;
  workspaces: number | null;
  status: EntityStatus;
  primaryDomain: string | null;
  brandingColor: string | null;
  createdAt: string | null;
}

export interface BusinessUnit {
  id: string;
  organizationId: string;
  name: string;
  region: string | null;
  headcount: number | null;
  owner: string | null;
  status: EntityStatus;
}

/* ------------------------------------------------------------------ users */

export interface AdminUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  avatarUrl: string | null;
  role: string;
  department: string | null;
  organization: string | null;
  status: EntityStatus;
  lastLoginAt: string | null;
  mfaStatus: MfaStatus;
  createdAt: string | null;
}

export interface AdminUserPage {
  items: AdminUser[];
  page: number;
  size: number;
  total: number;
}

/* ------------------------------------------------------------------- rbac */

export type PermissionEffect = "ALLOW" | "DENY" | "CONDITIONAL";

export interface RolePermission {
  resource: string;
  actions: string[];
  effect: PermissionEffect;
}

export interface RoleDefinition {
  id: string;
  name: string;
  description: string;
  scope: "PLATFORM" | "ORGANIZATION" | "WORKSPACE" | "CUSTOM";
  privilegeLevel: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
  builtIn: boolean;
  permissions: RolePermission[];
  resources: string[];
  assignedUsers: number | null;
}

/* ------------------------------------------------------------- workspaces */

export interface Workspace {
  id: string;
  name: string;
  organization: string | null;
  region: string | null;
  members: number | null;
  teams: number | null;
  storageUsedBytes: number | null;
  storageQuotaBytes: number | null;
  defaultPolicy: string | null;
  status: EntityStatus;
}

/* ----------------------------------------------------------------- policy */

export type PolicyState = "ENFORCED" | "MONITORED" | "DISABLED" | "NOT_CONFIGURED";

export interface SecurityPolicy {
  id: string;
  name: string;
  category: "IDENTITY" | "SESSION" | "DEVICE" | "NETWORK" | "APPLICATION";
  description: string;
  state: PolicyState;
  appliesTo: string;
  lastUpdatedAt: string | null;
}

/* ----------------------------------------------------------- integrations */

export type IntegrationState = "CONNECTED" | "AVAILABLE" | "COMING_SOON" | "ERROR";

export interface Integration {
  id: string;
  name: string;
  category: "API" | "SIEM" | "SOAR" | "MESSAGING" | "EMAIL" | "THREAT_INTEL" | "AI";
  description: string;
  state: IntegrationState;
  docsPath: string | null;
}

export interface ApiKeySummary {
  id: string;
  label: string;
  prefix: string;
  scopes: string[];
  createdAt: string | null;
  lastUsedAt: string | null;
  status: EntityStatus;
}

export interface WebhookSubscription {
  id: string;
  endpoint: string;
  events: string[];
  status: EntityStatus;
  lastDeliveryAt: string | null;
  failureCount: number | null;
}

/* ------------------------------------------------------------- audit logs */

export type AuditOutcome = "SUCCESS" | "FAILURE" | "DENIED" | "PENDING";

export interface AdminAuditEntry {
  id: string;
  timestamp: string;
  actor: string;
  actorEmail: string | null;
  action: string;
  target: string | null;
  outcome: AuditOutcome;
  ipAddress: string | null;
  device: string | null;
  location: string | null;
}

/* ---------------------------------------------------------------- session */

export interface UserSession {
  id: string;
  user: string;
  browser: string | null;
  device: string | null;
  os: string | null;
  location: string | null;
  ipAddress: string | null;
  loginAt: string | null;
  lastSeenAt: string | null;
  current: boolean;
  status: "ACTIVE" | "IDLE" | "EXPIRED" | "REVOKED";
}

/* ---------------------------------------------------------------- billing */

export interface BillingOverview {
  planName: string | null;
  planTier: "COMMUNITY" | "PROFESSIONAL" | "ENTERPRISE" | null;
  seatsUsed: number | null;
  seatsIncluded: number | null;
  storageUsedBytes: number | null;
  storageQuotaBytes: number | null;
  renewalDate: string | null;
  paymentMethod: string | null;
  invoices: BillingInvoice[];
}

export interface BillingInvoice {
  id: string;
  number: string;
  issuedAt: string;
  amount: number;
  currency: string;
  status: "PAID" | "DUE" | "OVERDUE" | "VOID";
}

/* ------------------------------------------------------- identity / trust */

export interface IdentityProvider {
  id: string;
  name: string;
  protocol: "SAML" | "OIDC" | "OAUTH2" | "SCIM";
  status: IntegrationState;
  domains: string[];
  lastSyncAt: string | null;
}

export interface MfaOverview {
  enrolledUsers: number | null;
  totalUsers: number | null;
  enforcement: "OFF" | "OPTIONAL" | "REQUIRED" | "UNKNOWN";
  methods: string[];
}

/* ----------------------------------------------------- system configuration */

export interface SystemConfiguration {
  environment: "DEVELOPMENT" | "STAGING" | "PRODUCTION" | "UNKNOWN";
  maintenanceMode: boolean | null;
  platformName: string | null;
  supportEmail: string | null;
  defaultLocale: string | null;
  defaultTimeZone: string | null;
  retentionDays: number | null;
}

/* ------------------------------------------------------- account settings */

export interface AccountPreferences {
  language: string | null;
  timeZone: string | null;
  theme: "DARK" | "LIGHT" | "SYSTEM" | null;
  density: "COMFORTABLE" | "COMPACT" | null;
  reducedMotion: boolean | null;
  highContrast: boolean | null;
  emailDigest: boolean | null;
  criticalAlerts: boolean | null;
}
