import type { FrameworkId } from "@/types/governance";

/**
 * Static, publicly documented structure of each compliance framework.
 *
 * This is reference material only — control names and clause numbers come from
 * the published standards. No coverage percentage, score or status is defined
 * here; those arrive exclusively from the assessment backend.
 */
export interface FrameworkRequirement {
  id: string;
  title: string;
  description: string;
}

export interface FrameworkDefinition {
  id: FrameworkId;
  slug: string;
  name: string;
  shortName: string;
  authority: string;
  focus: string;
  overview: string;
  auditCycle: string;
  requirements: FrameworkRequirement[];
  /** Controls the platform can evidence once telemetry is wired in. */
  platformSupport: string[];
}

export const FRAMEWORKS: FrameworkDefinition[] = [
  {
    id: "ISO_27001",
    slug: "iso-27001",
    name: "ISO/IEC 27001:2022",
    shortName: "ISO 27001",
    authority: "International Organization for Standardization",
    focus: "Information Security Management System (ISMS)",
    overview:
      "ISO/IEC 27001 specifies the requirements for establishing, implementing, maintaining and continually improving an information security management system, with Annex A listing 93 controls across organisational, people, physical and technological themes.",
    auditCycle:
      "Certification audit, then surveillance audits annually and recertification every 3 years",
    requirements: [
      {
        id: "A.5",
        title: "Organisational controls",
        description:
          "Policies, roles, supplier relationships, threat intelligence and incident management governance.",
      },
      {
        id: "A.6",
        title: "People controls",
        description: "Screening, terms of employment, awareness training and disciplinary process.",
      },
      {
        id: "A.7",
        title: "Physical controls",
        description: "Secure areas, equipment protection, clear desk and secure disposal.",
      },
      {
        id: "A.8",
        title: "Technological controls",
        description:
          "Access control, cryptography, logging, monitoring, network security and secure development.",
      },
      {
        id: "Cl.6",
        title: "Planning",
        description: "Risk assessment, risk treatment plan and Statement of Applicability.",
      },
      {
        id: "Cl.9",
        title: "Performance evaluation",
        description: "Monitoring, measurement, internal audit and management review.",
      },
    ],
    platformSupport: [
      "A.8.15 Logging — packet inspection and audit log retention",
      "A.8.16 Monitoring activities — SOC alerting and threat detection",
      "A.5.7 Threat intelligence — intelligence feed and IOC management",
      "A.5.24–A.5.28 Incident management — incident response centre and evidence vault",
    ],
  },
  {
    id: "SOC_2",
    slug: "soc-2",
    name: "AICPA SOC 2 (Trust Services Criteria)",
    shortName: "SOC 2",
    authority: "American Institute of Certified Public Accountants",
    focus: "Service organisation controls for security, availability and confidentiality",
    overview:
      "SOC 2 reports attest a service organisation's controls against the Trust Services Criteria. Type I assesses design at a point in time; Type II assesses operating effectiveness across an observation window, typically 3–12 months.",
    auditCycle: "Type II observation window of 3–12 months, reported annually",
    requirements: [
      {
        id: "CC1",
        title: "Control environment",
        description: "Integrity, board oversight, structure, competence and accountability.",
      },
      {
        id: "CC2",
        title: "Communication and information",
        description: "Internal and external communication of security objectives.",
      },
      {
        id: "CC3",
        title: "Risk assessment",
        description: "Objectives, risk identification, fraud risk and change assessment.",
      },
      {
        id: "CC4",
        title: "Monitoring activities",
        description: "Ongoing evaluations and communication of deficiencies.",
      },
      {
        id: "CC5",
        title: "Control activities",
        description: "Selection and deployment of controls and technology.",
      },
      {
        id: "CC6",
        title: "Logical and physical access",
        description: "Identity, credentials, provisioning and physical safeguards.",
      },
      {
        id: "CC7",
        title: "System operations",
        description: "Vulnerability detection, monitoring, incident response and recovery.",
      },
      {
        id: "CC8",
        title: "Change management",
        description: "Authorised, designed, tested and approved changes.",
      },
      {
        id: "CC9",
        title: "Risk mitigation",
        description: "Business disruption and vendor risk mitigation.",
      },
    ],
    platformSupport: [
      "CC7.2 Detection — continuous monitoring and anomaly detection",
      "CC7.3–CC7.4 Incident evaluation and response workflows",
      "CC6.1 Logical access — role-based access and authentication logs",
      "CC4.1 Monitoring — dashboard evidence and audit trail export",
    ],
  },
  {
    id: "NIST_CSF",
    slug: "nist-csf",
    name: "NIST Cybersecurity Framework 2.0",
    shortName: "NIST CSF",
    authority: "US National Institute of Standards and Technology",
    focus: "Outcome-based cybersecurity risk management",
    overview:
      "NIST CSF 2.0 organises cybersecurity outcomes into six functions — Govern, Identify, Protect, Detect, Respond and Recover — supported by categories, subcategories and implementation tiers rather than pass/fail controls.",
    auditCycle: "Continuous self-assessment against target profile; no formal certification",
    requirements: [
      {
        id: "GV",
        title: "Govern",
        description: "Strategy, roles, policy, oversight and supply chain risk management.",
      },
      {
        id: "ID",
        title: "Identify",
        description: "Asset management, risk assessment and improvement.",
      },
      {
        id: "PR",
        title: "Protect",
        description: "Identity, access, awareness, data security and platform resilience.",
      },
      {
        id: "DE",
        title: "Detect",
        description: "Continuous monitoring and adverse event analysis.",
      },
      {
        id: "RS",
        title: "Respond",
        description: "Incident management, analysis, reporting and mitigation.",
      },
      {
        id: "RC",
        title: "Recover",
        description: "Incident recovery plan execution and communication.",
      },
    ],
    platformSupport: [
      "DE.CM Continuous monitoring — deep packet inspection telemetry",
      "DE.AE Adverse event analysis — threat detection engine and correlation",
      "RS.MA / RS.AN Incident management and analysis — incident centre",
      "ID.RA Risk assessment — risk register and heat mapping",
    ],
  },
  {
    id: "PCI_DSS",
    slug: "pci-dss",
    name: "PCI DSS v4.0",
    shortName: "PCI-DSS",
    authority: "PCI Security Standards Council",
    focus: "Protection of cardholder data environments",
    overview:
      "PCI DSS v4.0 defines 12 principal requirements covering network security, cardholder data protection, vulnerability management, access control, monitoring and information security policy for any entity storing, processing or transmitting cardholder data.",
    auditCycle: "Annual assessment (ROC or SAQ) with quarterly network scanning",
    requirements: [
      {
        id: "R1",
        title: "Network security controls",
        description:
          "Install and maintain network security controls between trusted and untrusted networks.",
      },
      {
        id: "R2",
        title: "Secure configurations",
        description: "Apply secure configurations to all system components.",
      },
      {
        id: "R3",
        title: "Protect stored account data",
        description: "Minimise storage and render PAN unreadable.",
      },
      {
        id: "R4",
        title: "Protect data in transit",
        description: "Strong cryptography over open, public networks.",
      },
      {
        id: "R5",
        title: "Malicious software",
        description: "Protect systems and networks from malware.",
      },
      {
        id: "R6",
        title: "Secure systems and software",
        description: "Develop and maintain secure systems.",
      },
      {
        id: "R7",
        title: "Restrict access",
        description: "Restrict access to system components by business need to know.",
      },
      { id: "R8", title: "Identify users", description: "Identify users and authenticate access." },
      {
        id: "R10",
        title: "Log and monitor",
        description: "Log and monitor all access to system components and cardholder data.",
      },
      {
        id: "R11",
        title: "Test security",
        description: "Test security of systems and networks regularly.",
      },
      {
        id: "R12",
        title: "Policy and programmes",
        description: "Support information security with organisational policies.",
      },
    ],
    platformSupport: [
      "Req 10.2 Audit logs — capture and retention of network activity",
      "Req 10.6 Time synchronisation evidence on captured traffic",
      "Req 11.5 Intrusion detection — network detection rules",
      "Req 1.2 Network security control validation via traffic analysis",
    ],
  },
  {
    id: "CIS_CONTROLS",
    slug: "cis-controls",
    name: "CIS Critical Security Controls v8.1",
    shortName: "CIS Controls",
    authority: "Center for Internet Security",
    focus: "Prioritised safeguards grouped into implementation groups IG1–IG3",
    overview:
      "The CIS Controls define 18 controls and 153 safeguards prioritised by implementation group, mapping directly to NIST CSF and ISO 27001 for organisations that want a prescriptive starting point.",
    auditCycle: "Continuous self-assessment via CIS-CAT or equivalent tooling",
    requirements: [
      {
        id: "C1",
        title: "Inventory of enterprise assets",
        description: "Actively manage all hardware assets.",
      },
      {
        id: "C2",
        title: "Inventory of software assets",
        description: "Actively manage all software on the network.",
      },
      { id: "C3", title: "Data protection", description: "Identify, classify and protect data." },
      {
        id: "C4",
        title: "Secure configuration",
        description: "Secure configuration of assets and software.",
      },
      {
        id: "C6",
        title: "Access control management",
        description: "Grant, revoke and review access rights.",
      },
      {
        id: "C8",
        title: "Audit log management",
        description: "Collect, alert, review and retain audit logs.",
      },
      {
        id: "C13",
        title: "Network monitoring and defence",
        description: "Detect and respond to network threats.",
      },
      {
        id: "C17",
        title: "Incident response management",
        description: "Establish an incident response programme.",
      },
    ],
    platformSupport: [
      "Control 8 Audit log management — inspection and activity logs",
      "Control 13 Network monitoring and defence — DPI and detection rules",
      "Control 17 Incident response — case management and playbooks",
      "Control 1/2 asset discovery from observed network conversations",
    ],
  },
  {
    id: "GDPR",
    slug: "gdpr",
    name: "EU General Data Protection Regulation",
    shortName: "GDPR",
    authority: "European Union",
    focus: "Lawful processing and protection of personal data",
    overview:
      "GDPR governs the processing of personal data of individuals in the EU/EEA. Security-relevant obligations centre on Article 32 (security of processing), Articles 33–34 (breach notification) and Article 30 (records of processing).",
    auditCycle: "Ongoing; supervisory authority inspection on demand, 72-hour breach notification",
    requirements: [
      {
        id: "Art.5",
        title: "Processing principles",
        description:
          "Lawfulness, minimisation, accuracy, storage limitation, integrity and confidentiality.",
      },
      {
        id: "Art.30",
        title: "Records of processing",
        description: "Maintain records of processing activities.",
      },
      {
        id: "Art.32",
        title: "Security of processing",
        description: "Pseudonymisation, encryption, resilience and regular testing.",
      },
      {
        id: "Art.33",
        title: "Breach notification",
        description: "Notify the supervisory authority within 72 hours.",
      },
      {
        id: "Art.34",
        title: "Communication to data subjects",
        description: "Inform data subjects of high-risk breaches.",
      },
      {
        id: "Art.35",
        title: "Data protection impact assessment",
        description: "Assess high-risk processing before it starts.",
      },
    ],
    platformSupport: [
      "Art.32(1)(d) Regular testing — detection efficacy reporting",
      "Art.33 Breach notification — incident timeline and evidence chain",
      "Art.32(1)(b) Confidentiality monitoring — exfiltration detection",
      "Art.30 Records — audit trail of security processing activity",
    ],
  },
  {
    id: "HIPAA",
    slug: "hipaa",
    name: "HIPAA Security Rule",
    shortName: "HIPAA",
    authority: "US Department of Health and Human Services",
    focus: "Safeguards for electronic protected health information (ePHI)",
    overview:
      "The HIPAA Security Rule requires administrative, physical and technical safeguards to ensure the confidentiality, integrity and availability of ePHI, with required and addressable implementation specifications.",
    auditCycle: "Ongoing; OCR audit on demand, annual risk analysis expected",
    requirements: [
      {
        id: "164.308",
        title: "Administrative safeguards",
        description: "Risk analysis, sanction policy, workforce security and incident procedures.",
      },
      {
        id: "164.310",
        title: "Physical safeguards",
        description: "Facility access, workstation use and device controls.",
      },
      {
        id: "164.312",
        title: "Technical safeguards",
        description:
          "Access control, audit controls, integrity, authentication and transmission security.",
      },
      {
        id: "164.314",
        title: "Organisational requirements",
        description: "Business associate contracts and group health plan requirements.",
      },
      {
        id: "164.316",
        title: "Policies and documentation",
        description: "Maintain policies and retain documentation for six years.",
      },
    ],
    platformSupport: [
      "164.312(b) Audit controls — recorded network and system activity",
      "164.312(e) Transmission security — encrypted traffic verification",
      "164.308(a)(6) Security incident procedures — incident workflow",
      "164.308(a)(1)(ii)(A) Risk analysis — risk register inputs",
    ],
  },
];

export const FRAMEWORK_BY_SLUG = new Map(
  FRAMEWORKS.map((framework) => [framework.slug, framework]),
);
export const FRAMEWORK_BY_ID = new Map(FRAMEWORKS.map((framework) => [framework.id, framework]));
