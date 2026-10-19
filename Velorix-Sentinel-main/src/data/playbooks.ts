/**
 * Security playbook doctrine (NIST SP 800-61r2 lifecycle).
 *
 * This is reference material — standard response procedure, not incident data.
 * Nothing here describes a real or fabricated event in this tenant.
 */

export type PlaybookPhaseId =
  "preparation" | "identification" | "containment" | "eradication" | "recovery" | "lessons-learned";

export interface PlaybookPhase {
  id: PlaybookPhaseId;
  title: string;
  objective: string;
  steps: string[];
}

export interface Playbook {
  id: string;
  name: string;
  summary: string;
  category: string;
  severityFocus: "CRITICAL" | "HIGH" | "MEDIUM";
  estimatedDuration: string;
  mitreTactics: string[];
  phases: PlaybookPhase[];
}

export const PHASE_ORDER: PlaybookPhaseId[] = [
  "preparation",
  "identification",
  "containment",
  "eradication",
  "recovery",
  "lessons-learned",
];

export const PHASE_LABELS: Record<PlaybookPhaseId, string> = {
  preparation: "Preparation",
  identification: "Identification",
  containment: "Containment",
  eradication: "Eradication",
  recovery: "Recovery",
  "lessons-learned": "Lessons Learned",
};

export const playbooks: Playbook[] = [
  {
    id: "malware-response",
    name: "Malware Response",
    summary:
      "Standard procedure for a confirmed malware execution on one or more managed endpoints.",
    category: "MALWARE",
    severityFocus: "HIGH",
    estimatedDuration: "4–12 hours",
    mitreTactics: ["Execution", "Persistence", "Defense Evasion", "Command and Control"],
    phases: [
      {
        id: "preparation",
        title: "Preparation",
        objective: "Ensure tooling, baselines and authority are in place before an event.",
        steps: [
          "Maintain a current asset inventory with owner and criticality ratings.",
          "Verify EDR agent coverage and packet capture sensors on all critical segments.",
          "Keep known-good software baselines and hashes for rapid diffing.",
          "Confirm the on-call rota, escalation path and legal contact list.",
          "Pre-stage forensic collection tooling and clean removable media.",
        ],
      },
      {
        id: "identification",
        title: "Identification",
        objective: "Confirm malicious code executed and determine scope.",
        steps: [
          "Correlate the detection with process ancestry, command line and parent hash.",
          "Extract file hashes and submit to the threat intelligence indicator store.",
          "Review outbound connections around execution time in the packet record.",
          "Determine the initial access vector (email, removable media, exploit, supply chain).",
          "Enumerate every host that observed the same hash, mutex or C2 destination.",
        ],
      },
      {
        id: "containment",
        title: "Containment",
        objective: "Stop propagation while preserving forensic value.",
        steps: [
          "Network-isolate affected hosts rather than powering them off.",
          "Block C2 domains and IPs at the perimeter and DNS resolver.",
          "Suspend credentials observed in use by the malicious process.",
          "Snapshot volatile memory and disk before remediation.",
          "Apply a temporary blocking rule for the file hash across the estate.",
        ],
      },
      {
        id: "eradication",
        title: "Eradication",
        objective: "Remove the adversary's foothold completely.",
        steps: [
          "Remove persistence: services, scheduled tasks, run keys, WMI subscriptions, cron.",
          "Patch or reconfigure the exploited vulnerability or misconfiguration.",
          "Rebuild hosts where kernel-level or bootkit persistence is suspected.",
          "Rotate all secrets accessible from the compromised host.",
          "Re-scan the environment for residual indicators.",
        ],
      },
      {
        id: "recovery",
        title: "Recovery",
        objective: "Return systems to production with confidence.",
        steps: [
          "Restore from a backup verified to predate the initial compromise.",
          "Return hosts to production in monitored stages.",
          "Raise logging verbosity on recovered hosts for a defined watch period.",
          "Validate business function with the system owner before closing containment.",
          "Confirm no re-infection across two full monitoring cycles.",
        ],
      },
      {
        id: "lessons-learned",
        title: "Lessons Learned",
        objective: "Convert the incident into durable defensive improvement.",
        steps: [
          "Hold a blameless review within five working days of closure.",
          "Record dwell time, MTTD and MTTR against the team's targets.",
          "Convert every manual detection step into an automated rule where possible.",
          "Update the asset inventory and network diagram with what was learned.",
          "File remediation actions with named owners and due dates.",
        ],
      },
    ],
  },
  {
    id: "ransomware-response",
    name: "Ransomware Response",
    summary:
      "Procedure for encryption events, including double-extortion data theft considerations.",
    category: "RANSOMWARE",
    severityFocus: "CRITICAL",
    estimatedDuration: "24–96 hours",
    mitreTactics: ["Initial Access", "Lateral Movement", "Exfiltration", "Impact"],
    phases: [
      {
        id: "preparation",
        title: "Preparation",
        objective: "Guarantee recoverability before an encryption event occurs.",
        steps: [
          "Maintain immutable, offline-tested backups with documented restore times.",
          "Segment the network so a single compromised subnet cannot reach backup infrastructure.",
          "Pre-agree crisis communication, insurance and law-enforcement contacts.",
          "Rehearse a full restore of a tier-1 system at least twice a year.",
          "Restrict and monitor administrative shares and remote management protocols.",
        ],
      },
      {
        id: "identification",
        title: "Identification",
        objective: "Confirm encryption, identify the family and assess data theft.",
        steps: [
          "Identify the ransomware family from note, extension and encryption behaviour.",
          "Establish the encryption start time and the earliest evidence of intrusion.",
          "Look for staging and bulk outbound transfer preceding encryption.",
          "Determine whether backups or hypervisors were targeted.",
          "Classify the data at risk for regulatory notification purposes.",
        ],
      },
      {
        id: "containment",
        title: "Containment",
        objective: "Halt encryption spread immediately.",
        steps: [
          "Isolate affected segments; disable SMB and RDP between zones.",
          "Disable the compromised domain accounts and rotate KRBTGT twice.",
          "Take backup infrastructure offline from the production network.",
          "Preserve one encrypted host untouched for forensic and decryption analysis.",
          "Freeze automated backup rotation to prevent good copies being aged out.",
        ],
      },
      {
        id: "eradication",
        title: "Eradication",
        objective: "Remove operator access across the entire estate.",
        steps: [
          "Hunt for remote access tooling and tunnelling utilities left by the operator.",
          "Rebuild domain controllers if tier-0 compromise is confirmed.",
          "Reset every privileged credential and service account.",
          "Remove all deployment mechanisms used to distribute the payload.",
          "Validate no scheduled re-execution remains in Group Policy or software deployment.",
        ],
      },
      {
        id: "recovery",
        title: "Recovery",
        objective: "Restore business operations in a prioritised, verified order.",
        steps: [
          "Restore in business-criticality order onto a clean, rebuilt network.",
          "Verify integrity of restored data with the data owner before release.",
          "Apply hardened baselines to every rebuilt host.",
          "Maintain enhanced monitoring for a minimum of 30 days.",
          "Only consider ransom questions through legal and executive channels — never technically.",
        ],
      },
      {
        id: "lessons-learned",
        title: "Lessons Learned",
        objective: "Close the gaps that allowed encryption at scale.",
        steps: [
          "Document the full intrusion chain from initial access to impact.",
          "Measure actual restore time against the documented RTO.",
          "Close the segmentation and privileged-access gaps exploited.",
          "Report to regulators and affected parties within statutory deadlines.",
          "Re-test the recovery plan against the observed technique set.",
        ],
      },
    ],
  },
  {
    id: "phishing-investigation",
    name: "Phishing Investigation",
    summary: "Triage and response for reported or detected phishing campaigns.",
    category: "PHISHING",
    severityFocus: "MEDIUM",
    estimatedDuration: "1–4 hours",
    mitreTactics: ["Initial Access", "Credential Access"],
    phases: [
      {
        id: "preparation",
        title: "Preparation",
        objective: "Make reporting easy and analysis fast.",
        steps: [
          "Provide a one-click report-phish control in the mail client.",
          "Maintain a detonation sandbox for attachments and links.",
          "Keep mail gateway search and bulk-purge permissions ready for the on-call analyst.",
          "Run recurring awareness exercises and track reporting rates.",
        ],
      },
      {
        id: "identification",
        title: "Identification",
        objective: "Determine legitimacy, scope and whether anyone interacted.",
        steps: [
          "Analyse full headers: SPF, DKIM, DMARC, originating IP and reply-to mismatch.",
          "Detonate links and attachments in isolation; capture the final landing page.",
          "Search the mail gateway for every recipient of the campaign.",
          "Identify which recipients clicked, submitted credentials or opened attachments.",
          "Extract indicators for the intelligence store.",
        ],
      },
      {
        id: "containment",
        title: "Containment",
        objective: "Remove reach and prevent credential misuse.",
        steps: [
          "Purge the message from every mailbox in the estate.",
          "Block sender, domain and URL at gateway and proxy.",
          "Force password reset and session revocation for anyone who submitted credentials.",
          "Review authentication logs for successful logins from unusual locations.",
        ],
      },
      {
        id: "eradication",
        title: "Eradication",
        objective: "Remove any foothold established through the campaign.",
        steps: [
          "Remove attacker-created mailbox rules, forwarding and delegate permissions.",
          "Revoke any OAuth application consent granted during the campaign.",
          "Scan endpoints of users who opened attachments.",
          "Report the phishing infrastructure to the hosting provider and registrar.",
        ],
      },
      {
        id: "recovery",
        title: "Recovery",
        objective: "Return affected users to normal operation safely.",
        steps: [
          "Restore mailbox configuration to the approved baseline.",
          "Re-enable accounts after MFA re-enrolment.",
          "Notify affected users with clear, non-punitive guidance.",
          "Watch affected accounts for anomalous access for 14 days.",
        ],
      },
      {
        id: "lessons-learned",
        title: "Lessons Learned",
        objective: "Reduce susceptibility to the next campaign.",
        steps: [
          "Track click rate, report rate and time-to-first-report.",
          "Tighten DMARC policy and external-sender banners where gaps were exploited.",
          "Add the campaign's pattern to detection content.",
          "Brief the wider organisation on the specific lure used.",
        ],
      },
    ],
  },
  {
    id: "credential-theft",
    name: "Credential Theft",
    summary: "Response to stolen, dumped or brute-forced authentication material.",
    category: "CREDENTIAL_THEFT",
    severityFocus: "HIGH",
    estimatedDuration: "4–24 hours",
    mitreTactics: ["Credential Access", "Lateral Movement", "Persistence"],
    phases: [
      {
        id: "preparation",
        title: "Preparation",
        objective: "Limit the value of any single stolen credential.",
        steps: [
          "Enforce phishing-resistant MFA on all privileged and remote access.",
          "Adopt tiered administration so tier-0 credentials never touch workstations.",
          "Enable credential-dumping protections (LSA protection, Credential Guard).",
          "Monitor for leaked corporate credentials in external breach corpora.",
        ],
      },
      {
        id: "identification",
        title: "Identification",
        objective: "Determine which credentials were exposed and how they are being used.",
        steps: [
          "Identify the theft technique: dumping, keylogging, phishing, token theft or reuse.",
          "Enumerate every account cached or used on the affected host.",
          "Review authentication telemetry for impossible travel and anomalous clients.",
          "Check for Kerberos abuse: ticket requests, encryption downgrade, delegation changes.",
        ],
      },
      {
        id: "containment",
        title: "Containment",
        objective: "Invalidate the stolen material quickly.",
        steps: [
          "Reset the affected credentials and revoke all active sessions and refresh tokens.",
          "Disable accounts showing confirmed adversary use.",
          "Restrict the source host from authenticating to sensitive systems.",
          "Block the observed adversary infrastructure.",
        ],
      },
      {
        id: "eradication",
        title: "Eradication",
        objective: "Remove persistence created with the stolen identity.",
        steps: [
          "Audit for new accounts, group memberships and privileged role assignments.",
          "Review certificate templates, delegation settings and service principals for abuse.",
          "Reset service accounts and update dependent configuration.",
          "Rotate KRBTGT twice where domain compromise is suspected.",
        ],
      },
      {
        id: "recovery",
        title: "Recovery",
        objective: "Restore identity hygiene.",
        steps: [
          "Re-enrol affected users onto MFA with verified identity proofing.",
          "Return accounts to service with least-privilege membership.",
          "Monitor authentication for the affected identities for 30 days.",
          "Confirm dependent automation works after credential rotation.",
        ],
      },
      {
        id: "lessons-learned",
        title: "Lessons Learned",
        objective: "Prevent recurrence of the same access path.",
        steps: [
          "Document how the credential was exposed and which control failed.",
          "Expand MFA and conditional-access coverage to the gap found.",
          "Add detection content for the specific abuse technique observed.",
          "Review the privileged-access model against the observed blast radius.",
        ],
      },
    ],
  },
  {
    id: "data-exfiltration",
    name: "Data Exfiltration",
    summary: "Response to unauthorised bulk movement of data out of the environment.",
    category: "DATA_EXFILTRATION",
    severityFocus: "CRITICAL",
    estimatedDuration: "12–72 hours",
    mitreTactics: ["Collection", "Command and Control", "Exfiltration"],
    phases: [
      {
        id: "preparation",
        title: "Preparation",
        objective: "Know where sensitive data lives and how it should move.",
        steps: [
          "Maintain a data classification map with owners for each sensitive store.",
          "Baseline normal egress volumes per host, protocol and destination.",
          "Ensure full packet or flow retention on egress paths.",
          "Agree regulatory notification thresholds and timelines with legal in advance.",
        ],
      },
      {
        id: "identification",
        title: "Identification",
        objective: "Establish what left, when and to where.",
        steps: [
          "Quantify transferred volume by host, protocol and destination from the packet record.",
          "Identify staging archives and the collection scope on source systems.",
          "Check for covert channels: DNS tunnelling, ICMP payloads, cloud storage APIs.",
          "Classify the data involved and count affected records or data subjects.",
        ],
      },
      {
        id: "containment",
        title: "Containment",
        objective: "Stop the transfer without destroying evidence.",
        steps: [
          "Block the destination infrastructure and the protocol path used.",
          "Isolate staging hosts while preserving memory and disk.",
          "Revoke credentials and API keys used to access the data store.",
          "Apply egress rate limits or deny rules on the affected segment.",
        ],
      },
      {
        id: "eradication",
        title: "Eradication",
        objective: "Remove the access path to the data.",
        steps: [
          "Remove the adversary's access mechanism and any persistence.",
          "Correct the over-permissive access that allowed bulk collection.",
          "Remove staged archives after forensic preservation.",
          "Rotate every credential and key with reach into the affected store.",
        ],
      },
      {
        id: "recovery",
        title: "Recovery",
        objective: "Restore controlled access and meet obligations.",
        steps: [
          "Reinstate access under least privilege with monitoring on the data store.",
          "Complete regulatory and contractual notifications within deadlines.",
          "Support affected parties per the agreed communications plan.",
          "Keep enhanced egress monitoring for a defined watch period.",
        ],
      },
      {
        id: "lessons-learned",
        title: "Lessons Learned",
        objective: "Reduce data exposure and improve egress visibility.",
        steps: [
          "Record dwell time between collection start and detection.",
          "Add egress-volume detection thresholds tuned to the observed pattern.",
          "Reduce data retention and access scope where it exceeded business need.",
          "Review third-party and cloud egress paths for the same weakness.",
        ],
      },
    ],
  },
  {
    id: "insider-threat",
    name: "Insider Threat",
    summary: "Handling of malicious or negligent activity by an authorised individual.",
    category: "INSIDER_THREAT",
    severityFocus: "HIGH",
    estimatedDuration: "Days to weeks",
    mitreTactics: ["Collection", "Exfiltration", "Impact"],
    phases: [
      {
        id: "preparation",
        title: "Preparation",
        objective: "Establish lawful, proportionate handling before suspicion arises.",
        steps: [
          "Agree an insider process jointly with HR, legal and privacy.",
          "Define what monitoring is lawful in each jurisdiction the company operates in.",
          "Implement least privilege and separation of duties on sensitive functions.",
          "Ensure joiner-mover-leaver processes revoke access promptly.",
        ],
      },
      {
        id: "identification",
        title: "Identification",
        objective: "Assess the concern discreetly and objectively.",
        steps: [
          "Restrict case visibility to a named, minimal handling group.",
          "Corroborate the behavioural signal with technical evidence before acting.",
          "Distinguish negligence and policy breach from deliberate malicious intent.",
          "Preserve evidence to an evidentiary standard from the outset.",
        ],
      },
      {
        id: "containment",
        title: "Containment",
        objective: "Limit harm without alerting the subject prematurely.",
        steps: [
          "Coordinate every visible action with HR and legal before execution.",
          "Reduce access quietly where possible rather than disabling abruptly.",
          "Preserve mailbox, endpoint and file access history immediately.",
          "Control physical and removable-media access where relevant.",
        ],
      },
      {
        id: "eradication",
        title: "Eradication",
        objective: "Remove the access and any planted mechanism.",
        steps: [
          "Revoke all corporate access on separation, including SaaS and shared accounts.",
          "Search for backdoor accounts, forwarding rules and scheduled tasks they created.",
          "Recover or wipe corporate devices and remote-wipe unreturned assets.",
          "Rotate any shared credential the individual knew.",
        ],
      },
      {
        id: "recovery",
        title: "Recovery",
        objective: "Restore team operation and data integrity.",
        steps: [
          "Verify the integrity of systems and data the individual administered.",
          "Transfer ownership of accounts, repositories and documents to a named successor.",
          "Support the affected team with a factual, confidential briefing.",
          "Confirm business continuity for functions they solely operated.",
        ],
      },
      {
        id: "lessons-learned",
        title: "Lessons Learned",
        objective: "Improve controls without creating a culture of suspicion.",
        steps: [
          "Review how long excessive privilege persisted before the event.",
          "Strengthen offboarding automation where manual gaps were found.",
          "Add separation-of-duties controls to the function that was abused.",
          "Review the process itself for proportionality and privacy compliance.",
        ],
      },
    ],
  },
];

export function findPlaybook(id: string): Playbook | undefined {
  return playbooks.find((playbook) => playbook.id === id);
}
