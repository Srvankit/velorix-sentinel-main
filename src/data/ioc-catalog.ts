import type { LucideIcon } from "lucide-react";
import {
  AtSign,
  Binary,
  Cpu,
  FileDigit,
  Fingerprint,
  Globe,
  Link2,
  Network,
  ScrollText,
} from "lucide-react";

/** Indicator classes supported by the IOC management workspace. */
export type IocKind =
  "ip" | "domain" | "url" | "hash" | "email" | "registry" | "mutex" | "process" | "certificate";

export interface IocCategory {
  kind: IocKind;
  label: string;
  plural: string;
  icon: LucideIcon;
  description: string;
  /** Canonical shape shown in the empty state so analysts know what to import. */
  example: string;
}

export const IOC_CATEGORIES: IocCategory[] = [
  {
    kind: "ip",
    label: "IP Address",
    plural: "IP Addresses",
    icon: Network,
    description: "IPv4/IPv6 addresses linked to C2, scanning or exfiltration infrastructure.",
    example: "203.0.113.24",
  },
  {
    kind: "domain",
    label: "Domain",
    plural: "Domains",
    icon: Globe,
    description: "Registered domains and DGA output observed in resolution or TLS SNI.",
    example: "malicious-example.test",
  },
  {
    kind: "url",
    label: "URL",
    plural: "URLs",
    icon: Link2,
    description: "Full request paths used for delivery, phishing or payload staging.",
    example: "https://example.test/update/payload.bin",
  },
  {
    kind: "hash",
    label: "File Hash",
    plural: "File Hashes",
    icon: FileDigit,
    description: "MD5, SHA-1 and SHA-256 digests of known-bad or suspicious artifacts.",
    example: "sha256:0000…0000",
  },
  {
    kind: "email",
    label: "Email Address",
    plural: "Email Addresses",
    icon: AtSign,
    description: "Sender and reply-to addresses tied to phishing or BEC campaigns.",
    example: "billing@example.test",
  },
  {
    kind: "registry",
    label: "Registry Key",
    plural: "Registry Keys",
    icon: ScrollText,
    description: "Windows registry paths used for persistence or configuration storage.",
    example: "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
  },
  {
    kind: "mutex",
    label: "Mutex",
    plural: "Mutexes",
    icon: Binary,
    description: "Named synchronisation objects used by malware to avoid double execution.",
    example: "Global\\ExampleMutex",
  },
  {
    kind: "process",
    label: "Process",
    plural: "Processes",
    icon: Cpu,
    description: "Process names and command-line patterns associated with malicious execution.",
    example: "powershell.exe -enc …",
  },
  {
    kind: "certificate",
    label: "Certificate",
    plural: "Certificates",
    icon: Fingerprint,
    description: "TLS certificate fingerprints and issuers seen on adversary infrastructure.",
    example: "JA3S / SHA-1 thumbprint",
  },
];

export const IOC_SEVERITIES = ["Critical", "High", "Medium", "Low", "Informational"] as const;
export const IOC_STATUSES = ["Active", "Monitoring", "Expired", "False Positive"] as const;
export const IOC_CONFIDENCE = ["Confirmed", "High", "Medium", "Low"] as const;

export type IocSeverity = (typeof IOC_SEVERITIES)[number];
export type IocStatus = (typeof IOC_STATUSES)[number];
export type IocConfidence = (typeof IOC_CONFIDENCE)[number];

/** Shape the backend must return for `/api/v1/intel/iocs`. */
export interface IocRecord {
  id: string;
  kind: IocKind;
  value: string;
  severity: IocSeverity;
  confidence: IocConfidence;
  status: IocStatus;
  source: string;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}
