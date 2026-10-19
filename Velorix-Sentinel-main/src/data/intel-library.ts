/**
 * Threat intelligence knowledge library — doctrine and framework reference.
 * Static educational content only; no campaign or actor telemetry.
 */

export interface LibraryEntry {
  slug: string;
  title: string;
  category: "Adversaries" | "Frameworks" | "Operations";
  summary: string;
  points: string[];
  readingMinutes: number;
}

export const LIBRARY_ENTRIES: LibraryEntry[] = [
  {
    slug: "apt-groups",
    title: "APT Groups",
    category: "Adversaries",
    summary:
      "How advanced persistent threat groups are tracked, named and clustered across vendors, and why attribution is probabilistic.",
    points: [
      "Clustering relies on infrastructure, tooling and tradecraft overlap",
      "Vendor naming schemes differ; map to a canonical internal identifier",
      "Attribution confidence should always be stated explicitly in reporting",
    ],
    readingMinutes: 7,
  },
  {
    slug: "attack-techniques",
    title: "Attack Techniques",
    category: "Frameworks",
    summary:
      "Technique-level thinking: describing adversary behaviour independent of tooling so detections survive malware churn.",
    points: [
      "Prefer behavioural detections over static signatures",
      "Map each detection to at least one ATT&CK technique",
      "Track detection coverage as a first-class metric",
    ],
    readingMinutes: 6,
  },
  {
    slug: "threat-actors",
    title: "Threat Actors",
    category: "Adversaries",
    summary:
      "Actor categories — nation state, criminal, hacktivist, insider — and how motivation shapes expected behaviour.",
    points: [
      "Motivation predicts dwell time and target selection",
      "Criminal ecosystems specialise: access brokers, affiliates, launderers",
      "Insider risk requires HR and identity signals, not just network data",
    ],
    readingMinutes: 8,
  },
  {
    slug: "security-frameworks",
    title: "Security Frameworks",
    category: "Frameworks",
    summary: "NIST CSF, ISO 27001, CIS Controls and how they relate to day-to-day SOC operations.",
    points: [
      "Frameworks define outcomes, not detections",
      "Map controls to telemetry sources to expose blind spots",
      "Audit evidence is easier when detections cite control identifiers",
    ],
    readingMinutes: 9,
  },
  {
    slug: "kill-chain",
    title: "Cyber Kill Chain",
    category: "Frameworks",
    summary:
      "The seven-stage intrusion model and where network-derived evidence contributes most in each stage.",
    points: [
      "Reconnaissance and delivery are visible at the perimeter",
      "Command-and-control is the strongest network signal",
      "Actions-on-objectives usually need endpoint corroboration",
    ],
    readingMinutes: 6,
  },
  {
    slug: "zero-trust",
    title: "Zero Trust",
    category: "Operations",
    summary: "Identity-centric architecture: never trust, always verify, and assume breach.",
    points: [
      "Every request is authenticated, authorised and encrypted",
      "Micro-segmentation limits blast radius of a single compromise",
      "Continuous verification requires continuous telemetry",
    ],
    readingMinutes: 7,
  },
  {
    slug: "soc-playbooks",
    title: "SOC Playbooks",
    category: "Operations",
    summary: "Repeatable triage procedures that keep analyst decisions consistent under pressure.",
    points: [
      "Each playbook states entry criteria, steps, and exit criteria",
      "Enrichment steps should be automated wherever possible",
      "Review playbooks after every major incident",
    ],
    readingMinutes: 5,
  },
  {
    slug: "incident-response",
    title: "Incident Response",
    category: "Operations",
    summary: "Preparation, detection, containment, eradication, recovery and lessons learned.",
    points: [
      "Containment decisions are business decisions — pre-agree thresholds",
      "Preserve evidence before remediation where legally required",
      "Post-incident review must produce tracked engineering work",
    ],
    readingMinutes: 8,
  },
];
