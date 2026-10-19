import {
  Bug,
  Crosshair,
  Fingerprint,
  Network,
  ScrollText,
  ShieldCheck,
  Siren,
  Target,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

/**
 * Security Knowledge Center reference library.
 *
 * Static, curated documentation authored for analysts. It is deliberately
 * separate from the copilot: nothing here is model-generated.
 */
export interface KnowledgeTopic {
  slug: string;
  title: string;
  tagline: string;
  category: "Frameworks" | "Vulnerabilities" | "Operations" | "Fundamentals";
  icon: LucideIcon;
  readingMinutes: number;
  body: string;
}

export const KNOWLEDGE_TOPICS: KnowledgeTopic[] = [
  {
    slug: "mitre-attack",
    title: "MITRE ATT&CK",
    tagline: "Adversary tactics, techniques and procedures mapped to real intrusions.",
    category: "Frameworks",
    icon: Target,
    readingMinutes: 6,
    body: `## What it is

MITRE ATT&CK is a curated knowledge base of adversary behaviour observed in real intrusions. It is organised as a matrix: **tactics** (the adversary's goal) across the top, **techniques** (how the goal is achieved) beneath each one.

## Enterprise tactics

| Tactic | Adversary goal |
| --- | --- |
| Reconnaissance | Gather information to plan the operation |
| Initial Access | Get a foothold in the environment |
| Execution | Run adversary-controlled code |
| Persistence | Survive reboots and credential changes |
| Privilege Escalation | Obtain higher-level permissions |
| Defense Evasion | Avoid detection |
| Credential Access | Steal account names and secrets |
| Discovery | Learn the internal environment |
| Lateral Movement | Move between hosts |
| Collection | Gather data of interest |
| Command and Control | Communicate with compromised systems |
| Exfiltration | Steal data |
| Impact | Manipulate, interrupt or destroy |

## Why it matters in packet analysis

Network telemetry is strongest for a handful of tactics. Discovery shows up as scanning, Command and Control as beaconing, Lateral Movement as unusual east-west sessions, and Exfiltration as asymmetric outbound volume.

> Map every detection rule to a technique ID. It turns an alert list into a coverage map, and gaps become obvious.

## Using it day to day

1. Tag each detection with its technique (for example \`T1046 — Network Service Discovery\`).
2. Chart which tactics your rules cover and which are blind.
3. During an investigation, walk the matrix forward: if you see Discovery, hunt for the Lateral Movement that usually follows.`,
  },
  {
    slug: "owasp-top-10",
    title: "OWASP Top 10",
    tagline: "The consensus list of the most critical web application security risks.",
    category: "Vulnerabilities",
    icon: ShieldCheck,
    readingMinutes: 5,
    body: `## Overview

The OWASP Top 10 is a periodically refreshed awareness document describing the most impactful categories of web application risk. It is a starting point for an application security programme, not an exhaustive checklist.

## The categories

- **Broken Access Control** — users acting outside their intended permissions.
- **Cryptographic Failures** — data exposed in transit or at rest through weak or missing crypto.
- **Injection** — untrusted input interpreted as a command or query.
- **Insecure Design** — missing or ineffective control design, not an implementation slip.
- **Security Misconfiguration** — defaults, verbose errors, unnecessary features left enabled.
- **Vulnerable and Outdated Components** — unpatched libraries and runtimes.
- **Identification and Authentication Failures** — weak session and credential handling.
- **Software and Data Integrity Failures** — unverified updates and CI/CD pipelines.
- **Security Logging and Monitoring Failures** — breaches that go unnoticed.
- **Server-Side Request Forgery** — the server fetches an attacker-chosen URL.

## What shows on the wire

Injection attempts, credential stuffing and SSRF callbacks are often visible in captured traffic long before an application log confirms them.

\`\`\`text
POST /api/search HTTP/1.1
Host: intranet.internal
X-Forwarded-For: 10.0.0.9

q=' OR 1=1--
\`\`\`

> Treat repeated 4xx bursts from a single source as an enumeration signal, not noise.`,
  },
  {
    slug: "cve-database",
    title: "CVE Database",
    tagline: "How vulnerability identifiers, scoring and exploitability data fit together.",
    category: "Vulnerabilities",
    icon: ScrollText,
    readingMinutes: 4,
    body: `## Identifiers

A **CVE** is a unique identifier for a publicly disclosed vulnerability, formatted \`CVE-YYYY-NNNNN\`. It says nothing about severity on its own — it is a name, not a score.

## Scoring

**CVSS** provides a base score from 0.0 to 10.0 built from exploitability and impact metrics.

| Range | Rating |
| --- | --- |
| 9.0 – 10.0 | Critical |
| 7.0 – 8.9 | High |
| 4.0 – 6.9 | Medium |
| 0.1 – 3.9 | Low |

**EPSS** estimates the probability that a vulnerability will be exploited in the wild in the next 30 days. **KEV** (Known Exploited Vulnerabilities) lists what is already being exploited.

## A practical triage order

1. Is it on KEV? Patch now.
2. Is EPSS high and the asset internet-facing? Patch this cycle.
3. High CVSS but unreachable and unauthenticated paths blocked? Schedule normally.

> Never prioritise on CVSS alone. Reachability and exposure change the real risk more than the base score does.`,
  },
  {
    slug: "threat-hunting",
    title: "Threat Hunting",
    tagline: "Hypothesis-driven search for adversaries that evaded detection.",
    category: "Operations",
    icon: Crosshair,
    readingMinutes: 6,
    body: `## The premise

Hunting assumes a compromise has already bypassed preventive controls. Instead of waiting for an alert, the analyst forms a hypothesis and tests it against telemetry.

## The loop

1. **Hypothesise** — "an internal host is beaconing to a rare external destination".
2. **Gather** — select the captures and time window that would contain the evidence.
3. **Analyse** — aggregate by destination, look for low-variance intervals and small, regular payloads.
4. **Conclude** — confirm, refute, or refine.
5. **Operationalise** — turn a confirmed pattern into a detection rule so it never needs hunting again.

## Network hunt starters

- Long-lived sessions with near-identical inter-packet timing (beaconing).
- DNS queries with high entropy labels (tunnelling or DGA).
- Internal hosts talking to internal hosts they have never contacted before.
- Outbound volume far exceeding inbound on a non-backup host.

> A hunt that finds nothing is still valuable — it converts an unknown into a measured absence.`,
  },
  {
    slug: "ioc-guide",
    title: "IOC Guide",
    tagline: "Indicators of Compromise: types, quality and how long they stay useful.",
    category: "Fundamentals",
    icon: Fingerprint,
    readingMinutes: 4,
    body: `## What an IOC is

An Indicator of Compromise is an observable artefact suggesting an intrusion — a hash, a domain, an IP, a registry key, a JA3 fingerprint.

## The pyramid of pain

Indicators differ in how much they cost an adversary to change.

| Indicator | Cost to the adversary |
| --- | --- |
| Hash values | Trivial |
| IP addresses | Easy |
| Domain names | Simple |
| Network artefacts | Annoying |
| Tools | Challenging |
| TTPs | Hard |

Detections built on behaviour outlast detections built on values.

## Quality checklist

- **Context** — what campaign, what confidence, who reported it.
- **Validity window** — an IP from two years ago is likely a false positive today.
- **Scope** — is it a shared CDN address, or a dedicated adversary host?

> Always attach an expiry to atomic indicators. Stale IOC feeds generate more false positives than detections.`,
  },
  {
    slug: "malware-types",
    title: "Malware Types",
    tagline: "Families, behaviours and the traces each one leaves on the network.",
    category: "Fundamentals",
    icon: Bug,
    readingMinutes: 5,
    body: `## Families

- **Ransomware** — encrypts data and extorts payment. Preceded by discovery, credential theft and staging.
- **Trojan / Loader** — establishes access and pulls follow-on payloads.
- **Remote Access Trojan** — interactive control of a host.
- **Worm** — self-propagates across the network without user action.
- **Infostealer** — harvests credentials, tokens and wallets, then exfiltrates in one burst.
- **Rootkit / Bootkit** — hides at or below the operating system.
- **Cryptominer** — consumes CPU, contacts mining pools on predictable ports.
- **Botnet client** — awaits instructions over a command channel.

## Network-visible behaviour

| Behaviour | Signal |
| --- | --- |
| Beaconing | Regular small outbound requests at a fixed jittered interval |
| Payload staging | Executable download from a rare host shortly after initial access |
| Lateral movement | SMB, RDP or WinRM sessions between workstations |
| Exfiltration | Sustained outbound transfer far above the host's baseline |

> Endpoint agents see the file. The capture sees the conversation. Correlating both is what closes an investigation.`,
  },
  {
    slug: "network-protocols",
    title: "Network Protocols",
    tagline: "The protocol behaviour an analyst must recognise on sight.",
    category: "Fundamentals",
    icon: Network,
    readingMinutes: 6,
    body: `## Layers that matter in a capture

| Layer | Examples | Analyst use |
| --- | --- | --- |
| Link | Ethernet, ARP | Host identity, spoofing, poisoning |
| Network | IPv4, IPv6, ICMP | Routing, reachability, sweeps |
| Transport | TCP, UDP | Session state, scanning, floods |
| Application | HTTP, DNS, TLS, SMB | Intent and content |

## TCP flags at a glance

\`\`\`text
SYN      connection request
SYN/ACK  connection accepted
ACK      acknowledgement
FIN      graceful close
RST      abrupt reset
PSH/URG  unusual in bulk — common in crafted scans
\`\`\`

A half-open scan shows many \`SYN\` packets answered by \`RST\`, with no completed handshakes.

## DNS

DNS is the richest low-volume signal in most captures: unusually long labels, high query rates to a single domain, or \`TXT\` records carrying base64 all indicate tunnelling.

## TLS

Even encrypted, the handshake exposes SNI, cipher preferences and certificate details — enough to fingerprint the client and spot mismatches between the SNI and the destination.`,
  },
  {
    slug: "security-best-practices",
    title: "Security Best Practices",
    tagline: "Controls that reduce the blast radius of the incidents you will still have.",
    category: "Operations",
    icon: Siren,
    readingMinutes: 5,
    body: `## Identity

- Enforce phishing-resistant multi-factor authentication on every administrative path.
- Remove standing privilege; grant it just in time and log every elevation.
- Store roles server-side and check them server-side, always.

## Network

- Segment by function, not by floor. Workstations should not route to each other.
- Default-deny egress and allowlist what the business genuinely needs.
- Capture at chokepoints and retain long enough to investigate a slow intrusion.

## Operations

- Patch on exposure and exploitability, not on score alone.
- Test restores, not backups.
- Write the incident runbook before the incident, and rehearse it.

## Detection

- Map coverage to a framework so gaps are measurable.
- Tune for precision — an alert nobody trusts is an alert nobody reads.
- Keep an audit trail of who acknowledged, escalated and closed each detection.

> Assume breach. Design so that a single compromised credential does not become a full-estate outage.`,
  },
];

export function findTopic(slug: string): KnowledgeTopic | undefined {
  return KNOWLEDGE_TOPICS.find((topic) => topic.slug === slug);
}
