/**
 * MITRE ATT&CK Enterprise reference subset.
 *
 * This is public framework documentation (tactic / technique definitions), not
 * telemetry. Detection coverage is intentionally reported as "planned" until the
 * backend correlation engine publishes real coverage per technique.
 */

export type CoverageState = "planned" | "in-progress";

export interface MitreTechnique {
  id: string;
  name: string;
  description: string;
  detection: string;
  mitigation: string;
  coverage: CoverageState;
}

export interface MitreTactic {
  id: string;
  name: string;
  shortName: string;
  objective: string;
  techniques: MitreTechnique[];
}

export const MITRE_TACTICS: MitreTactic[] = [
  {
    id: "TA0001",
    name: "Initial Access",
    shortName: "initial-access",
    objective: "Adversary is trying to get into your network.",
    techniques: [
      {
        id: "T1566",
        name: "Phishing",
        description:
          "Adversaries send targeted messages containing malicious attachments or links to obtain execution or credentials.",
        detection:
          "Correlate inbound mail gateway verdicts with first-seen domains and outbound HTTP requests within minutes of delivery.",
        mitigation:
          "Attachment sandboxing, link rewriting, DMARC/DKIM/SPF enforcement and recurring user awareness training.",
        coverage: "planned",
      },
      {
        id: "T1190",
        name: "Exploit Public-Facing Application",
        description:
          "Exploitation of an internet-facing host, typically a web application, API or VPN appliance, to gain a foothold.",
        detection:
          "Watch for anomalous request bodies, unusual response sizes and post-exploitation child processes on edge hosts.",
        mitigation: "Patch management SLAs, WAF rules, network segmentation of DMZ hosts.",
        coverage: "planned",
      },
      {
        id: "T1133",
        name: "External Remote Services",
        description:
          "Valid accounts used against VPN, RDP or Citrix gateways to enter the environment without exploitation.",
        detection:
          "Impossible-travel logins, new ASN sources and off-hours gateway authentication.",
        mitigation: "MFA on every remote entry point, conditional access, session recording.",
        coverage: "planned",
      },
    ],
  },
  {
    id: "TA0002",
    name: "Execution",
    shortName: "execution",
    objective: "Adversary is trying to run malicious code.",
    techniques: [
      {
        id: "T1059",
        name: "Command and Scripting Interpreter",
        description:
          "Use of PowerShell, cmd, bash or scripting hosts to execute payloads on a compromised endpoint.",
        detection:
          "Script-block logging, encoded command flags, unusual parent/child process trees.",
        mitigation: "Constrained language mode, application control, signed-script enforcement.",
        coverage: "planned",
      },
      {
        id: "T1204",
        name: "User Execution",
        description:
          "The victim is convinced to open a malicious file or link that launches the payload.",
        detection:
          "Office applications spawning interpreters; archive extraction followed by execution.",
        mitigation: "Macro blocking, mark-of-the-web enforcement, protected view.",
        coverage: "planned",
      },
    ],
  },
  {
    id: "TA0003",
    name: "Persistence",
    shortName: "persistence",
    objective: "Adversary is trying to maintain their foothold.",
    techniques: [
      {
        id: "T1547",
        name: "Boot or Logon Autostart Execution",
        description:
          "Registry run keys, startup folders and services configured to relaunch the payload.",
        detection: "Baseline autostart extensibility points and alert on drift.",
        mitigation: "Least privilege, application control, monitored golden images.",
        coverage: "planned",
      },
      {
        id: "T1053",
        name: "Scheduled Task/Job",
        description:
          "Tasks, cron entries or systemd timers used to re-execute tooling on a schedule.",
        detection: "Task creation events referencing user-writable paths or LOLBins.",
        mitigation: "Restrict task creation rights, audit scheduler configuration.",
        coverage: "planned",
      },
    ],
  },
  {
    id: "TA0004",
    name: "Privilege Escalation",
    shortName: "privilege-escalation",
    objective: "Adversary is trying to gain higher-level permissions.",
    techniques: [
      {
        id: "T1068",
        name: "Exploitation for Privilege Escalation",
        description: "Kernel or driver vulnerabilities abused to move from user to SYSTEM/root.",
        detection:
          "Process token changes, unexpected driver loads, crash telemetry near escalation.",
        mitigation: "Rapid patching, driver blocklists, exploit protection.",
        coverage: "planned",
      },
      {
        id: "T1548",
        name: "Abuse Elevation Control Mechanism",
        description:
          "Bypass of UAC, sudo policy or setuid binaries to elevate without exploitation.",
        detection: "Auto-elevating binaries with unusual command lines; sudo policy changes.",
        mitigation: "UAC at maximum, curated sudoers, removal of unnecessary setuid bits.",
        coverage: "planned",
      },
    ],
  },
  {
    id: "TA0005",
    name: "Defense Evasion",
    shortName: "defense-evasion",
    objective: "Adversary is trying to avoid being detected.",
    techniques: [
      {
        id: "T1070",
        name: "Indicator Removal",
        description:
          "Clearing event logs, deleting artifacts and timestomping to frustrate investigation.",
        detection: "Log service stop/clear events, gaps in continuous telemetry.",
        mitigation: "Immutable off-host log shipping, tamper protection on agents.",
        coverage: "planned",
      },
      {
        id: "T1027",
        name: "Obfuscated Files or Information",
        description:
          "Packing, encoding or encrypting payloads so static controls cannot inspect them.",
        detection:
          "High-entropy sections, long base64 command lines, unusual TLS JA3 fingerprints.",
        mitigation: "Content inspection at egress, allowlisting, memory scanning.",
        coverage: "in-progress",
      },
    ],
  },
  {
    id: "TA0006",
    name: "Credential Access",
    shortName: "credential-access",
    objective: "Adversary is trying to steal account names and passwords.",
    techniques: [
      {
        id: "T1110",
        name: "Brute Force",
        description:
          "Password guessing, spraying and credential stuffing against exposed services.",
        detection:
          "Authentication failure bursts per source, single-password-many-accounts patterns.",
        mitigation: "Lockout policy, MFA, breached-password screening.",
        coverage: "in-progress",
      },
      {
        id: "T1003",
        name: "OS Credential Dumping",
        description:
          "Extraction of credential material from LSASS, SAM, /etc/shadow or ccache files.",
        detection: "Handle opens against LSASS, shadow copy creation, known dumper signatures.",
        mitigation: "Credential Guard, protected process light, tiered administration.",
        coverage: "planned",
      },
    ],
  },
  {
    id: "TA0007",
    name: "Discovery",
    shortName: "discovery",
    objective: "Adversary is trying to figure out your environment.",
    techniques: [
      {
        id: "T1046",
        name: "Network Service Discovery",
        description: "Scanning hosts and ports to enumerate reachable services.",
        detection:
          "Fan-out of SYN packets to many ports/hosts from a single source in a short window.",
        mitigation: "Segmentation, host firewalls, deception assets.",
        coverage: "in-progress",
      },
      {
        id: "T1018",
        name: "Remote System Discovery",
        description: "Enumeration of hosts through DNS, SMB, LDAP or ARP sweeps.",
        detection: "Unusual LDAP query volume, ARP sweeps, reverse DNS enumeration.",
        mitigation: "Restrict directory read scope, monitor service accounts.",
        coverage: "planned",
      },
    ],
  },
  {
    id: "TA0008",
    name: "Lateral Movement",
    shortName: "lateral-movement",
    objective: "Adversary is trying to move through your environment.",
    techniques: [
      {
        id: "T1021",
        name: "Remote Services",
        description:
          "Use of RDP, SMB, SSH or WinRM with valid credentials to reach additional hosts.",
        detection:
          "First-time host pairs, service account interactive logons, lateral SMB write patterns.",
        mitigation: "Tiered admin model, jump hosts, host-based firewall east-west rules.",
        coverage: "planned",
      },
      {
        id: "T1570",
        name: "Lateral Tool Transfer",
        description: "Copying tooling between internal hosts over SMB, WinRM or HTTP.",
        detection: "Executable writes to admin shares; internal HTTP transfers of PE files.",
        mitigation: "Block admin share writes, application control on servers.",
        coverage: "planned",
      },
    ],
  },
  {
    id: "TA0009",
    name: "Collection",
    shortName: "collection",
    objective: "Adversary is trying to gather data of interest.",
    techniques: [
      {
        id: "T1005",
        name: "Data from Local System",
        description: "Staging documents, databases and configuration from a compromised host.",
        detection:
          "Mass file reads, archive creation in temp paths, unusual access to file shares.",
        mitigation: "DLP, data classification, least-privilege share ACLs.",
        coverage: "planned",
      },
      {
        id: "T1560",
        name: "Archive Collected Data",
        description: "Compression and encryption of staged data prior to exfiltration.",
        detection: "Archiver process execution followed by large outbound flows.",
        mitigation: "Egress inspection, archive tooling restrictions.",
        coverage: "planned",
      },
    ],
  },
  {
    id: "TA0010",
    name: "Exfiltration",
    shortName: "exfiltration",
    objective: "Adversary is trying to steal data.",
    techniques: [
      {
        id: "T1048",
        name: "Exfiltration Over Alternative Protocol",
        description: "Data pushed out over DNS, ICMP or non-standard ports to bypass web controls.",
        detection:
          "DNS TXT volume anomalies, oversized ICMP payloads, long-lived flows on rare ports.",
        mitigation: "DNS egress control, protocol allowlisting at the perimeter.",
        coverage: "in-progress",
      },
      {
        id: "T1041",
        name: "Exfiltration Over C2 Channel",
        description: "Stolen data returned through the existing command-and-control session.",
        detection: "Upload/download ratio inversion on beaconing sessions.",
        mitigation: "TLS inspection where lawful, egress proxy enforcement.",
        coverage: "planned",
      },
    ],
  },
  {
    id: "TA0040",
    name: "Impact",
    shortName: "impact",
    objective: "Adversary is trying to manipulate, interrupt or destroy systems and data.",
    techniques: [
      {
        id: "T1486",
        name: "Data Encrypted for Impact",
        description: "Ransomware encrypts files to disrupt operations and force payment.",
        detection: "Mass file rename/extension change, shadow copy deletion, ransom note writes.",
        mitigation: "Offline backups, controlled folder access, rapid isolation playbooks.",
        coverage: "planned",
      },
      {
        id: "T1498",
        name: "Network Denial of Service",
        description: "Volumetric or protocol floods that exhaust bandwidth or connection state.",
        detection: "SYN/UDP/ICMP rate anomalies per destination and source diversity spikes.",
        mitigation: "Upstream scrubbing, rate limiting, anycast distribution.",
        coverage: "in-progress",
      },
    ],
  },
];

export const MITRE_TECHNIQUE_COUNT = MITRE_TACTICS.reduce(
  (total, tactic) => total + tactic.techniques.length,
  0,
);
