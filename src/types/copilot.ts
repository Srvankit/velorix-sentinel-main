/**
 * AI Security Copilot domain types.
 *
 * The copilot is frontend-first: conversations live in browser storage until the
 * backend AI service is connected. Every shape here mirrors what the future
 * `/api/v1/ai/*` contract is expected to return, so swapping the transport is a
 * localised change inside `useCopilotSessions`.
 */

export type CopilotRole = "user" | "assistant" | "system";

export interface CopilotMessage {
  id: string;
  role: CopilotRole;
  /** Markdown body. Rendered with GFM (tables, lists, code fences). */
  content: string;
  createdAt: string;
  /** Present on assistant messages once a backend model answers. */
  model?: string;
  /** Set when the message represents a platform notice rather than a model reply. */
  notice?: boolean;
}

export interface CopilotSession {
  id: string;
  title: string;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
  messages: CopilotMessage[];
}

/** Shape persisted under the copilot storage key. */
export interface CopilotStore {
  version: 1;
  sessions: CopilotSession[];
}

export interface PromptSuggestion {
  id: string;
  label: string;
  prompt: string;
  category: "Posture" | "Operations" | "Knowledge" | "Reporting";
}

export interface AiProvider {
  id: string;
  name: string;
  description: string;
  status: "coming-soon";
}
