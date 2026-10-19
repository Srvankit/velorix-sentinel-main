/**
 * Compliance Knowledge Hub articles.
 *
 * Educational reference material summarising published standards and doctrines.
 * Contains no organisation-specific scores, findings or assessment results.
 */
export interface GovernanceArticle {
  slug: string;
  title: string;
  category: "Framework" | "Doctrine" | "Programme";
  summary: string;
  readMinutes: number;
  sections: { heading: string; body: string; points?: string[] }[];
}

export const GOVERNANCE_ARTICLES: GovernanceArticle[] = [
  {
    slug: "iso-27001",
    title: "ISO 27001 Overview",
    category: "Framework",
    summary:
      "How an ISMS is scoped, risk-assessed and certified under ISO/IEC 27001:2022, and what auditors expect to see.",
    readMinutes: 7,
    sections: [
      {
        heading: "What the standard requires",
        body: "ISO 27001 is a management-system standard: clauses 4–10 define the ISMS itself, while Annex A lists 93 reference controls you select through a Statement of Applicability.",
        points: [
          "Define scope and interested parties (clause 4)",
          "Run a repeatable risk assessment and treatment plan (clause 6)",
          "Operate, monitor, internally audit and management-review the ISMS (clauses 8–9)",
          "Justify inclusion or exclusion of every Annex A control",
        ],
      },
      {
        heading: "Certification path",
        body: "A Stage 1 documentation review is followed by a Stage 2 effectiveness audit. Certificates run three years with annual surveillance audits.",
      },
      {
        heading: "Where network telemetry helps",
        body: "Annex A.8.15 logging and A.8.16 monitoring are among the most commonly under-evidenced controls. Continuous capture analysis, detection alerts and retained audit trails give an auditor direct operational evidence rather than policy statements.",
      },
    ],
  },
  {
    slug: "soc-2",
    title: "SOC 2 Overview",
    category: "Framework",
    summary:
      "Trust Services Criteria, Type I versus Type II, and how observation windows change the evidence you must keep.",
    readMinutes: 6,
    sections: [
      {
        heading: "Trust Services Criteria",
        body: "Security (the common criteria, CC1–CC9) is mandatory. Availability, Confidentiality, Processing Integrity and Privacy are optional categories added to the report scope.",
      },
      {
        heading: "Type I vs Type II",
        body: "Type I opines on control design at a point in time. Type II opines on operating effectiveness across an observation window of typically 3–12 months, so evidence must be continuous, not reconstructed.",
      },
      {
        heading: "Evidence that satisfies CC7",
        body: "CC7 covers detection and response. Auditors sample alerts, triage records and incident closure notes. Timestamped, immutable records with named owners are far stronger than screenshots.",
        points: [
          "Detection configuration and change history",
          "Alert-to-incident traceability",
          "Post-incident review documentation",
        ],
      },
    ],
  },
  {
    slug: "nist-csf",
    title: "NIST Cybersecurity Framework 2.0",
    category: "Framework",
    summary:
      "The six functions, profiles and tiers — and how to use CSF as a board-level reporting language.",
    readMinutes: 6,
    sections: [
      {
        heading: "Six functions",
        body: "Govern, Identify, Protect, Detect, Respond and Recover. Govern was added in 2.0 to elevate strategy, roles, policy and supply-chain oversight.",
      },
      {
        heading: "Current and target profiles",
        body: "CSF is not pass/fail. You express a current profile and a target profile, then plan the gap. This makes it uniquely suited to executive reporting because progress is a trajectory, not a certificate.",
      },
      {
        heading: "Implementation tiers",
        body: "Tiers 1–4 (Partial, Risk Informed, Repeatable, Adaptive) describe the rigour of your risk governance, not the quantity of controls you own.",
      },
    ],
  },
  {
    slug: "pci-dss",
    title: "PCI-DSS Essentials",
    category: "Framework",
    summary:
      "Scoping the cardholder data environment and the v4.0 shift toward continuous, customised validation.",
    readMinutes: 6,
    sections: [
      {
        heading: "Scope is the whole game",
        body: "Any system that stores, processes or transmits cardholder data — plus anything connected to it — is in scope. Segmentation validated by network evidence is the primary way to reduce that scope.",
      },
      {
        heading: "What changed in v4.0",
        body: "v4.0 introduces the customised approach, more frequent authentication requirements, targeted risk analyses and expanded scripting/phishing controls. Future-dated requirements became mandatory in 2025.",
      },
      {
        heading: "Requirement 10 and 11",
        body: "Log every access to cardholder data and test detection regularly. Network traffic analysis substantiates both segmentation claims and intrusion-detection coverage.",
      },
    ],
  },
  {
    slug: "cis-controls",
    title: "CIS Controls v8.1",
    category: "Framework",
    summary:
      "18 controls, 153 safeguards and the implementation-group model for prioritising limited budget.",
    readMinutes: 5,
    sections: [
      {
        heading: "Implementation groups",
        body: "IG1 is basic cyber hygiene for small enterprises, IG2 adds controls for organisations managing sensitive data, IG3 targets mature organisations facing sophisticated adversaries.",
      },
      {
        heading: "Why CIS first",
        body: "CIS safeguards are prescriptive and testable, and map cleanly to NIST CSF and ISO 27001, so early CIS work is rarely wasted when a certification programme starts later.",
      },
      {
        heading: "Network-centric controls",
        body: "Controls 8 (audit log management), 12 (network infrastructure management) and 13 (network monitoring and defence) are where packet-level visibility contributes most directly.",
      },
    ],
  },
  {
    slug: "zero-trust",
    title: "Zero Trust Architecture",
    category: "Doctrine",
    summary:
      "NIST SP 800-207 principles, the policy decision/enforcement split, and realistic adoption stages.",
    readMinutes: 7,
    sections: [
      {
        heading: "Core principles",
        body: "No implicit trust from network location. Every access request is authenticated, authorised and continuously evaluated against device state, identity and behaviour.",
        points: [
          "Verify explicitly on every request",
          "Enforce least privilege with just-in-time access",
          "Assume breach and segment blast radius",
        ],
      },
      {
        heading: "Policy engine and enforcement point",
        body: "SP 800-207 separates the policy decision point (engine plus administrator) from the enforcement point that brokers each session. Telemetry quality determines decision quality.",
      },
      {
        heading: "Where monitoring fits",
        body: "Zero trust depends on continuous diagnostics. East-west traffic inspection is what proves segmentation actually holds rather than merely being configured.",
      },
    ],
  },
  {
    slug: "governance",
    title: "Security Governance",
    category: "Programme",
    summary:
      "Board oversight, policy hierarchy, ownership models and the reporting cadence executives actually read.",
    readMinutes: 6,
    sections: [
      {
        heading: "Policy hierarchy",
        body: "Policy states intent, standards make it measurable, procedures make it repeatable and guidelines advise. Auditors trace a control from policy to executed procedure.",
      },
      {
        heading: "Three lines model",
        body: "Operational management owns risk, risk and compliance functions oversee it, internal audit assures it independently. Confusing these lines is the most common governance finding.",
      },
      {
        heading: "Reporting cadence",
        body: "Operational metrics weekly, management reporting monthly, board reporting quarterly. Board packs should carry trend, exposure and decisions required — not raw alert counts.",
      },
    ],
  },
  {
    slug: "risk-management",
    title: "Cyber Risk Management",
    category: "Programme",
    summary:
      "Inherent vs residual risk, likelihood/impact scales, treatment options and quantification approaches.",
    readMinutes: 7,
    sections: [
      {
        heading: "Inherent and residual",
        body: "Inherent risk is exposure before controls; residual is what remains after them. Registers that record only one of the two cannot demonstrate control value.",
      },
      {
        heading: "Treatment options",
        body: "Mitigate, transfer, accept or avoid. Every acceptance needs a named accountable owner, an expiry date and a review trigger.",
      },
      {
        heading: "Qualitative vs quantitative",
        body: "Matrices communicate quickly but compress detail. Quantitative methods such as FAIR express exposure in currency, which is usually what a board wants when weighing investment.",
      },
    ],
  },
];

export const ARTICLE_BY_SLUG = new Map(GOVERNANCE_ARTICLES.map((a) => [a.slug, a]));
