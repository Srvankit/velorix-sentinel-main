import { describe, expect, test } from "bun:test";
import fs from "node:fs";
import path from "node:path";

describe("AI Assistant Clarification & UI Transparency (Task 5.3)", () => {
  const rootDir = path.resolve(__dirname, "..");
  const routesDir = path.join(rootDir, "routes");
  const landingDir = path.join(rootDir, "components", "landing");
  const copilotDir = path.join(rootDir, "components", "copilot");
  const layoutsDir = path.join(rootDir, "layouts");
  const hooksDir = path.join(rootDir, "hooks");

  const assistantRoute = fs.readFileSync(path.join(routesDir, "assistant.tsx"), "utf-8");
  const assistantIndex = fs.readFileSync(path.join(routesDir, "assistant.index.tsx"), "utf-8");
  const assistantSession = fs.readFileSync(path.join(routesDir, "assistant.$sessionId.tsx"), "utf-8");
  const aiFeatures = fs.readFileSync(path.join(landingDir, "AiFeatures.tsx"), "utf-8");
  const copilotWorkspace = fs.readFileSync(path.join(copilotDir, "CopilotWorkspace.tsx"), "utf-8");
  const aiStatusPanel = fs.readFileSync(path.join(copilotDir, "AiStatusPanel.tsx"), "utf-8");
  const copilotEmptyState = fs.readFileSync(path.join(copilotDir, "CopilotEmptyState.tsx"), "utf-8");
  const navigation = fs.readFileSync(path.join(layoutsDir, "navigation.ts"), "utf-8");
  const copilotSessionsHook = fs.readFileSync(path.join(hooksDir, "useCopilotSessions.ts"), "utf-8");

  describe("Assistant Route Shell Gating & Transparency", () => {
    test("assistant.tsx labels AI Copilot as Experimental / Join Waitlist", () => {
      expect(assistantRoute).toContain("AI Security Copilot (Experimental / Join Waitlist)");
      expect(assistantRoute).toContain("data-testid=\"assistant-experimental-banner\"");
      expect(assistantRoute).toContain("AI Security Copilot — Experimental / Join Waitlist");
      expect(assistantRoute).toContain("Roadmap Preview");
    });

    test("assistant.tsx clarifies that no live LLM model is connected in beta", () => {
      expect(assistantRoute).toContain("No live LLM model (e.g. Gemini / OpenAI API) is connected during this public beta");
      expect(assistantRoute).toContain("Prompts are stored locally in browser storage for interface testing");
    });

    test("assistant.tsx provides interactive waitlist enrollment and dashboard recovery", () => {
      expect(assistantRoute).toContain("data-testid=\"join-waitlist-btn\"");
      expect(assistantRoute).toContain("Join Waitlist");
      expect(assistantRoute).toContain("velorix.copilot.waitlist_joined");
      expect(assistantRoute).toContain("Back to Dashboard");
      expect(assistantRoute).toContain("to=\"/dashboard\"");
    });

    test("assistant.index.tsx and assistant.$sessionId.tsx reflect experimental preview metadata", () => {
      expect(assistantIndex).toContain("Experimental / Join Waitlist");
      expect(assistantIndex).toContain("Roadmap Preview");
      expect(assistantSession).toContain("Experimental Preview");
      expect(assistantSession).toContain("Roadmap Preview");
    });
  });

  describe("Landing Page AI Features Reconciled to Roadmap", () => {
    test("AiFeatures.tsx uses truthful roadmap heading and description", () => {
      expect(aiFeatures).toContain("eyebrow=\"Roadmap Capabilities\"");
      expect(aiFeatures).toContain("title=\"AI-Assisted Threat Intelligence (Roadmap)\"");
      expect(aiFeatures).toContain("Planned post-beta LLM integrations (Google Gemini / OpenAI API)");
      expect(aiFeatures).toContain("No AI inference is simulated");
    });

    test("AiFeatures.tsx cards display Roadmap badges and non-deceptive titles", () => {
      expect(aiFeatures).toContain("AI Threat Summary (Roadmap)");
      expect(aiFeatures).toContain("Conversational Query Assistant (Roadmap)");
      expect(aiFeatures).toContain("Risk & Exposure Prediction (Roadmap)");
      expect(aiFeatures).toContain("Prescriptive Remediation (Roadmap)");
      expect(aiFeatures).toContain("CVE & Exploit Mapping (Roadmap)");
      expect(aiFeatures).toContain("Attack Timeline Explanation (Roadmap)");
      expect(aiFeatures).toContain("badge: \"Roadmap\"");
    });

    test("AiFeatures.tsx contains no claims of live model analysis or hallucination-free guarantees", () => {
      expect(aiFeatures.toLowerCase()).not.toContain("live ai assistant");
      expect(aiFeatures.toLowerCase()).not.toContain("trained on network truth");
    });
  });

  describe("Copilot Components Truthful Status", () => {
    test("CopilotWorkspace.tsx header badge indicates Experimental / Waitlist", () => {
      expect(copilotWorkspace).toContain("data-testid=\"copilot-experimental-badge\"");
      expect(copilotWorkspace).toContain("Experimental / Waitlist");
      expect(copilotWorkspace).not.toContain("Ready for AI Integration");
    });

    test("AiStatusPanel.tsx clarifies post-beta model deployment", () => {
      expect(aiStatusPanel).toContain("Experimental Preview · Roadmap Waitlist");
      expect(aiStatusPanel).toContain("Live model\n          inference (Google Gemini / OpenAI API) is scheduled for post-beta deployment.");
      expect(aiStatusPanel).not.toContain("Ready for AI Integration");
    });

    test("CopilotEmptyState.tsx explains local browser storage and absence of external API calls", () => {
      expect(copilotEmptyState).toContain("AI Copilot (Experimental Preview)");
      expect(copilotEmptyState).toContain("Stored locally in browser · No external AI API calls");
    });

    test("useCopilotSessions.ts informs users of pending post-beta AI backend", () => {
      expect(copilotSessionsHook).toContain("No AI model is connected yet (Experimental / Waitlist Preview)");
      expect(copilotSessionsHook).toContain("post-beta LLM inference service (e.g. Google Gemini / OpenAI)");
      expect(copilotSessionsHook).toContain("Nothing was sent to a third party");
    });
  });

  describe("Navigation Integrity", () => {
    test("navigation.ts excludes /assistant from primary sidebar navigation", () => {
      const activeNavBlock = navigation.slice(
        navigation.indexOf("export const navigation: NavSection[] = ["),
        navigation.indexOf("export const deferredNavigationItems: NavItem[] = [")
      );
      expect(activeNavBlock).not.toContain('to: "/assistant"');
    });

    test("navigation.ts marks AI Copilot with Experimental badge in deferred items", () => {
      expect(navigation).toContain('{ label: "AI Copilot", to: "/assistant", icon: BotMessageSquare, badge: "Experimental" }');
    });
  });
});
