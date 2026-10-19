import type { Integration, RoleDefinition, SecurityPolicy } from "@/types/admin";

/**
 * Static administration reference material.
 *
 * IMPORTANT: this file contains no tenant data. It defines the *catalogue* of
 * built-in roles, policy controls and integration targets the platform ships
 * with — structural metadata, not live figures. Anything measured (assigned
 * users, policy state, connection health) stays null / NOT_CONFIGURED until the
 * administration backend supplies it.
 */

export const BUILT_IN_ROLES: RoleDefinition[] = [
  {
    id: "super-admin",
    name: "Super Admin",
    description:
      "Unrestricted platform control across every organization, workspace and configuration surface.",
    scope: "PLATFORM",
    privilegeLevel: "CRITICAL",
    builtIn: true,
    assignedUsers: null,
    resources: ["Platform", "Organizations", "Users", "Roles", "Policies", "Billing", "System"],
    permissions: [
      { resource: "Platform configuration", actions: ["read", "write", "delete"], effect: "ALLOW" },
      { resource: "Organizations", actions: ["read", "write", "delete"], effect: "ALLOW" },
      {
        resource: "Users & roles",
        actions: ["read", "write", "delete", "impersonate"],
        effect: "ALLOW",
      },
      { resource: "Security policies", actions: ["read", "write"], effect: "ALLOW" },
      { resource: "Billing & licensing", actions: ["read", "write"], effect: "ALLOW" },
      { resource: "Audit logs", actions: ["read", "export"], effect: "ALLOW" },
    ],
  },
  {
    id: "organization-admin",
    name: "Organization Admin",
    description:
      "Full administrative authority inside a single organization, including users, workspaces and policy assignment.",
    scope: "ORGANIZATION",
    privilegeLevel: "HIGH",
    builtIn: true,
    assignedUsers: null,
    resources: ["Organization", "Users", "Workspaces", "Policies", "Integrations"],
    permissions: [
      { resource: "Organization profile", actions: ["read", "write"], effect: "ALLOW" },
      { resource: "Users", actions: ["read", "write", "invite", "suspend"], effect: "ALLOW" },
      { resource: "Workspaces", actions: ["read", "write", "delete"], effect: "ALLOW" },
      { resource: "Security policies", actions: ["read", "assign"], effect: "ALLOW" },
      { resource: "Platform configuration", actions: ["write"], effect: "DENY" },
      { resource: "Billing", actions: ["read"], effect: "CONDITIONAL" },
    ],
  },
  {
    id: "soc-manager",
    name: "SOC Manager",
    description:
      "Owns incident response operations, analyst assignment, escalation policy and SOC reporting.",
    scope: "ORGANIZATION",
    privilegeLevel: "HIGH",
    builtIn: true,
    assignedUsers: null,
    resources: ["Incidents", "Alerts", "Analysts", "Playbooks", "Reports"],
    permissions: [
      {
        resource: "Incidents & cases",
        actions: ["read", "write", "assign", "close"],
        effect: "ALLOW",
      },
      {
        resource: "Alert triage queue",
        actions: ["read", "write", "bulk-action"],
        effect: "ALLOW",
      },
      { resource: "Playbooks", actions: ["read", "write", "execute"], effect: "ALLOW" },
      { resource: "Executive reports", actions: ["read", "generate"], effect: "ALLOW" },
      { resource: "User administration", actions: ["write"], effect: "DENY" },
    ],
  },
  {
    id: "security-analyst",
    name: "Security Analyst",
    description:
      "Day-to-day triage, packet inspection review, investigation notes and evidence collection.",
    scope: "WORKSPACE",
    privilegeLevel: "MEDIUM",
    builtIn: true,
    assignedUsers: null,
    resources: ["Alerts", "Incidents", "Captures", "Threats", "Evidence"],
    permissions: [
      { resource: "Alerts", actions: ["read", "acknowledge", "escalate"], effect: "ALLOW" },
      { resource: "Incidents", actions: ["read", "comment", "attach-evidence"], effect: "ALLOW" },
      { resource: "Network captures", actions: ["read", "analyze"], effect: "ALLOW" },
      { resource: "Threat intelligence", actions: ["read"], effect: "ALLOW" },
      { resource: "Incident closure", actions: ["close"], effect: "CONDITIONAL" },
    ],
  },
  {
    id: "threat-hunter",
    name: "Threat Hunter",
    description:
      "Proactive hypothesis-driven hunting across captures, IOCs, MITRE techniques and historical telemetry.",
    scope: "WORKSPACE",
    privilegeLevel: "MEDIUM",
    builtIn: true,
    assignedUsers: null,
    resources: ["Captures", "IOCs", "MITRE", "Watchlists", "Queries"],
    permissions: [
      { resource: "Hunting queries", actions: ["read", "write", "execute"], effect: "ALLOW" },
      { resource: "IOC watchlists", actions: ["read", "write"], effect: "ALLOW" },
      { resource: "Historical telemetry", actions: ["read"], effect: "ALLOW" },
      { resource: "MITRE ATT&CK mapping", actions: ["read", "write"], effect: "ALLOW" },
      { resource: "User administration", actions: ["write"], effect: "DENY" },
    ],
  },
  {
    id: "compliance-officer",
    name: "Compliance Officer",
    description:
      "Read-oriented governance role for control evidence, audit trails and framework attestation.",
    scope: "ORGANIZATION",
    privilegeLevel: "MEDIUM",
    builtIn: true,
    assignedUsers: null,
    resources: ["Compliance", "Audit logs", "Risk register", "Reports", "Evidence"],
    permissions: [
      { resource: "Compliance frameworks", actions: ["read", "attest"], effect: "ALLOW" },
      { resource: "Audit logs", actions: ["read", "export"], effect: "ALLOW" },
      { resource: "Risk register", actions: ["read", "write"], effect: "ALLOW" },
      { resource: "Evidence vault", actions: ["read", "export"], effect: "ALLOW" },
      { resource: "Security policies", actions: ["write"], effect: "DENY" },
    ],
  },
  {
    id: "read-only",
    name: "Read Only",
    description:
      "Observation-only access for auditors, executives and stakeholders. No mutation of any resource.",
    scope: "ORGANIZATION",
    privilegeLevel: "LOW",
    builtIn: true,
    assignedUsers: null,
    resources: ["Dashboards", "Reports", "Incidents", "Threats"],
    permissions: [
      { resource: "Dashboards", actions: ["read"], effect: "ALLOW" },
      { resource: "Reports", actions: ["read"], effect: "ALLOW" },
      { resource: "Incidents", actions: ["read"], effect: "ALLOW" },
      { resource: "All write operations", actions: ["write", "delete"], effect: "DENY" },
    ],
  },
  {
    id: "custom-role",
    name: "Custom Role",
    description:
      "Template for tenant-defined roles. Permissions are composed per resource once role editing ships.",
    scope: "CUSTOM",
    privilegeLevel: "LOW",
    builtIn: false,
    assignedUsers: null,
    resources: ["Configurable"],
    permissions: [
      { resource: "Composed per resource", actions: ["configurable"], effect: "CONDITIONAL" },
    ],
  },
];

export const SECURITY_POLICY_CATALOGUE: SecurityPolicy[] = [
  {
    id: "password-policy",
    name: "Password Policy",
    category: "IDENTITY",
    description:
      "Minimum length, character classes, dictionary/breach checks and reuse history for local accounts.",
    state: "NOT_CONFIGURED",
    appliesTo: "All local identities",
    lastUpdatedAt: null,
  },
  {
    id: "password-rotation",
    name: "Password Rotation",
    category: "IDENTITY",
    description:
      "Maximum credential age, rotation reminders and forced reset windows for privileged accounts.",
    state: "NOT_CONFIGURED",
    appliesTo: "Privileged identities",
    lastUpdatedAt: null,
  },
  {
    id: "session-timeout",
    name: "Session Timeout",
    category: "SESSION",
    description:
      "Idle and absolute session lifetimes, refresh token rotation window and concurrent session limits.",
    state: "NOT_CONFIGURED",
    appliesTo: "All authenticated sessions",
    lastUpdatedAt: null,
  },
  {
    id: "device-trust",
    name: "Device Trust",
    category: "DEVICE",
    description:
      "Managed-device attestation, compliance posture signals and blocking of unregistered endpoints.",
    state: "NOT_CONFIGURED",
    appliesTo: "All endpoints",
    lastUpdatedAt: null,
  },
  {
    id: "network-restrictions",
    name: "Network Restrictions",
    category: "NETWORK",
    description:
      "Geo-fencing, ASN restrictions and blocking of anonymised egress (Tor, hosting providers, VPNs).",
    state: "NOT_CONFIGURED",
    appliesTo: "All inbound access",
    lastUpdatedAt: null,
  },
  {
    id: "ip-allowlist",
    name: "IP Allowlist",
    category: "NETWORK",
    description:
      "CIDR allowlists for console and API access, with break-glass exemptions for administrators.",
    state: "NOT_CONFIGURED",
    appliesTo: "Console & API",
    lastUpdatedAt: null,
  },
  {
    id: "security-headers",
    name: "Security Headers",
    category: "APPLICATION",
    description:
      "Content Security Policy, HSTS, frame-ancestors, referrer policy and permissions policy enforcement.",
    state: "NOT_CONFIGURED",
    appliesTo: "Web application edge",
    lastUpdatedAt: null,
  },
];

export const INTEGRATION_CATALOGUE: Integration[] = [
  {
    id: "rest-api",
    name: "REST API",
    category: "API",
    description:
      "Versioned JSON API at /api/v1 with bearer authentication, pagination and standard error envelopes.",
    state: "AVAILABLE",
    docsPath: "/knowledge",
  },
  {
    id: "webhooks",
    name: "Webhook Management",
    category: "API",
    description:
      "Signed outbound event delivery for incidents, alerts, analysis completion and administrative changes.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "api-keys",
    name: "API Keys",
    category: "API",
    description:
      "Scoped, rotatable service credentials with least-privilege scopes and last-used tracking.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "siem",
    name: "SIEM Integration",
    category: "SIEM",
    description:
      "Forward normalised detections to Splunk, Microsoft Sentinel, Elastic or QRadar over CEF/OCSF.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "soar",
    name: "SOAR Integration",
    category: "SOAR",
    description:
      "Trigger automated containment playbooks in Cortex XSOAR, Splunk SOAR or Tines from incidents.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "slack",
    name: "Slack",
    category: "MESSAGING",
    description: "Route critical alerts and case updates into dedicated SOC channels.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "teams",
    name: "Microsoft Teams",
    category: "MESSAGING",
    description: "Adaptive-card notifications with inline acknowledge and escalate actions.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "email",
    name: "Email",
    category: "EMAIL",
    description: "SMTP delivery for verification, digests, escalations and scheduled reports.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "virustotal",
    name: "VirusTotal",
    category: "THREAT_INTEL",
    description: "File hash, domain and IP reputation enrichment for observed indicators.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "abuseipdb",
    name: "AbuseIPDB",
    category: "THREAT_INTEL",
    description: "Community abuse confidence scoring for source addresses seen in captures.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "shodan",
    name: "Shodan",
    category: "THREAT_INTEL",
    description: "External exposure and banner context for hosts involved in detections.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "openai",
    name: "OpenAI",
    category: "AI",
    description: "Model backend option for the AI Security Copilot and detection summarisation.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "azure-openai",
    name: "Azure OpenAI",
    category: "AI",
    description: "Enterprise-hosted model deployment with tenant data residency guarantees.",
    state: "COMING_SOON",
    docsPath: null,
  },
  {
    id: "google-gemini",
    name: "Google Gemini",
    category: "AI",
    description: "Multimodal analysis backend for capture summarisation and threat narratives.",
    state: "COMING_SOON",
    docsPath: null,
  },
];

export const IDENTITY_CAPABILITIES = [
  {
    id: "mfa",
    name: "Multi-Factor Authentication",
    summary:
      "TOTP authenticator, WebAuthn security keys and push approval, with per-role enforcement tiers.",
  },
  {
    id: "sso",
    name: "Single Sign-On",
    summary:
      "Federated console access with just-in-time provisioning and domain-based routing to the correct IdP.",
  },
  {
    id: "saml",
    name: "SAML 2.0",
    summary:
      "SP-initiated and IdP-initiated flows, signed assertions, encrypted attributes and role attribute mapping.",
  },
  {
    id: "oauth",
    name: "OAuth 2.0",
    summary:
      "Authorization Code with PKCE for interactive apps and client credentials for machine-to-machine access.",
  },
  {
    id: "oidc",
    name: "OpenID Connect",
    summary:
      "Discovery document, JWKS-based token validation and standard claim mapping to platform roles.",
  },
  {
    id: "scim",
    name: "SCIM 2.0 Provisioning",
    summary:
      "Automated user and group lifecycle: create, update, deactivate and role sync from the identity source.",
  },
  {
    id: "session-policies",
    name: "Session Policies",
    summary:
      "Continuous evaluation, re-authentication on privilege elevation and revocation on risk signals.",
  },
  {
    id: "zero-trust",
    name: "Zero Trust",
    summary:
      "Never trust, always verify: per-request identity, device posture, network context and least privilege.",
  },
] as const;

export const SYSTEM_CONFIG_SECTIONS = [
  {
    id: "environment",
    name: "Environment",
    summary: "Runtime environment, API base URL, region and build metadata for this deployment.",
  },
  {
    id: "branding",
    name: "Platform Branding",
    summary: "Product name, logo, accent colour and login page presentation for the tenant.",
  },
  {
    id: "maintenance",
    name: "Maintenance Mode",
    summary:
      "Scheduled maintenance windows with an operator-defined banner and read-only fallback.",
  },
  {
    id: "email-templates",
    name: "Email Templates",
    summary: "Verification, password reset, invitation and escalation message templates.",
  },
  {
    id: "notification-templates",
    name: "Notification Templates",
    summary: "In-app, webhook and chat notification payload templates per event type.",
  },
  {
    id: "regional",
    name: "Regional Settings",
    summary: "Default locale, time zone, date format and data-residency region for the tenant.",
  },
] as const;
