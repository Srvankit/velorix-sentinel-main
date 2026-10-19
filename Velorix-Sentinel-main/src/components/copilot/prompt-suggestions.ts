import type { PromptSuggestion } from "@/types/copilot";

/**
 * Starter prompts shown on an empty copilot canvas. These are questions, never
 * answers — no response text is stored anywhere in the frontend.
 */
export const PROMPT_SUGGESTIONS: PromptSuggestion[] = [
  {
    id: "posture-today",
    label: "Today's security posture",
    prompt: "Explain today's security posture across my analysed captures.",
    category: "Posture",
  },
  {
    id: "active-investigations",
    label: "Summarize active investigations",
    prompt: "Summarize the active investigations and what needs analyst attention first.",
    category: "Operations",
  },
  {
    id: "system-health",
    label: "Show system health",
    prompt: "Show the current system health of the API, database and inspection worker.",
    category: "Operations",
  },
  {
    id: "ioc",
    label: "What are Indicators of Compromise?",
    prompt: "What are Indicators of Compromise, and which ones matter most in packet captures?",
    category: "Knowledge",
  },
  {
    id: "mitre",
    label: "Explain MITRE ATT&CK",
    prompt: "Explain the MITRE ATT&CK framework and how it maps to network detections.",
    category: "Knowledge",
  },
  {
    id: "soc-report",
    label: "Generate SOC report",
    prompt: "Generate an executive SOC report covering detections, risk and recommended actions.",
    category: "Reporting",
  },
  {
    id: "malware",
    label: "Explain malware behavior",
    prompt: "Explain common malware behaviours that are observable in network traffic.",
    category: "Knowledge",
  },
  {
    id: "suspicious-traffic",
    label: "Analyze suspicious traffic",
    prompt: "Analyze suspicious network traffic patterns and explain how to triage them.",
    category: "Operations",
  },
  {
    id: "cve",
    label: "Explain a CVE",
    prompt: "Explain how to assess the impact of a 2025 CVE against my network estate.",
    category: "Knowledge",
  },
  {
    id: "ransomware",
    label: "How ransomware spreads",
    prompt: "How do ransomware attacks spread laterally, and what network signals reveal it?",
    category: "Knowledge",
  },
];
