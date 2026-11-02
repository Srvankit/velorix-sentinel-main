import { describe, expect, test } from "bun:test";
import fs from "node:fs";
import path from "node:path";
import { ROUTES } from "@/utils/constants";

describe("Truthful Marketing Claims & Legal Links (Task 5.1)", () => {
  const landingDir = path.resolve(__dirname);
  const routesDir = path.resolve(__dirname, "../../routes");

  const faqContent = fs.readFileSync(path.join(landingDir, "Faq.tsx"), "utf-8");
  const featuresContent = fs.readFileSync(path.join(landingDir, "Features.tsx"), "utf-8");
  const heroContent = fs.readFileSync(path.join(landingDir, "Hero.tsx"), "utf-8");
  const pricingContent = fs.readFileSync(path.join(landingDir, "Pricing.tsx"), "utf-8");
  const workflowContent = fs.readFileSync(path.join(landingDir, "Workflow.tsx"), "utf-8");
  const footerContent = fs.readFileSync(path.join(landingDir, "Footer.tsx"), "utf-8");
  const registerContent = fs.readFileSync(path.join(routesDir, "register.tsx"), "utf-8");

  describe("Absence of Deceptive Claims (Text Audit)", () => {
    const deceptivePhrases = [
      "Splunk",
      "Chronicle",
      "Microsoft Sentinel",
      "JA3",
      "per-tenant keys",
      "live traffic",
      "SPAN/TAP",
      "Ensemble models",
    ];

    test("Faq.tsx contains no deceptive claims", () => {
      for (const phrase of deceptivePhrases) {
        expect(faqContent.toLowerCase().includes(phrase.toLowerCase())).toBe(false);
      }
    });

    test("Features.tsx contains no deceptive claims", () => {
      for (const phrase of deceptivePhrases) {
        expect(featuresContent.toLowerCase().includes(phrase.toLowerCase())).toBe(false);
      }
    });

    test("Hero.tsx contains no live traffic or deceptive claims", () => {
      for (const phrase of deceptivePhrases) {
        expect(heroContent.toLowerCase().includes(phrase.toLowerCase())).toBe(false);
      }
    });

    test("Pricing.tsx contains no unbacked SIEM integration or deceptive claims", () => {
      expect(pricingContent.includes("SIEM & SOAR integrations")).toBe(false);
      expect(pricingContent.toLowerCase().includes("splunk")).toBe(false);
      expect(pricingContent.toLowerCase().includes("live traffic")).toBe(false);
    });

    test("Workflow.tsx contains no sensor streaming or direct SIEM push claims", () => {
      expect(workflowContent.includes("stream from a sensor")).toBe(false);
      expect(workflowContent.includes("push to SIEM")).toBe(false);
    });
  });

  describe("Presence of Reconciled Honest Copy", () => {
    test("Faq.tsx accurately describes object storage tenant-isolation", () => {
      expect(faqContent).toContain("strict tenant-isolation and owner-scoped access control");
    });

    test("Faq.tsx accurately describes REST APIs and planned webhooks", () => {
      expect(faqContent).toContain("Export findings via standard JSON REST APIs (SIEM webhooks planned)");
    });

    test("Faq.tsx accurately specifies offline PCAP/PCAPNG capture analysis", () => {
      expect(faqContent).toContain("optimized for offline PCAP/PCAPNG capture analysis");
    });

    test("Faq.tsx clarifies 13-rule deterministic threat detection", () => {
      expect(faqContent).toContain("deterministic rule engine detecting scans, floods, brute-force, beaconing, and anomalous flows");
    });

    test("Hero.tsx accurately describes PCAP and PCAPNG capture inspection", () => {
      expect(heroContent).toContain("uploaded PCAP and PCAPNG captures");
      expect(heroContent).toContain("13-rule threat detection engine");
    });

    test("Pricing.tsx reflects realistic beta quotas and capabilities", () => {
      expect(pricingContent).toContain("Up to 500 MB per-user capture quota");
      expect(pricingContent).toContain("13-rule deterministic threat detection engine");
      expect(pricingContent).toContain("SIEM webhook forwarding (planned)");
    });
  });

  describe("Footer and Registration Legal Accessibility", () => {
    test("ROUTES contains privacy and terms endpoints", () => {
      expect(ROUTES.privacy).toBe("/privacy");
      expect(ROUTES.terms).toBe("/terms");
    });

    test("Footer.tsx includes accessible links to Privacy Policy and Terms of Service", () => {
      expect(footerContent).toContain('to="/privacy"');
      expect(footerContent).toContain("Privacy Policy");
      expect(footerContent).toContain('to="/terms"');
      expect(footerContent).toContain("Terms of Service");
    });

    test("register.tsx includes accessible links to Terms of Service and Privacy Policy", () => {
      expect(registerContent).toContain('to="/terms"');
      expect(registerContent).toContain('to="/privacy"');
    });

    test("Privacy route file exists and defines proper Route component", () => {
      const privacyPath = path.join(routesDir, "privacy.tsx");
      expect(fs.existsSync(privacyPath)).toBe(true);
      const content = fs.readFileSync(privacyPath, "utf-8");
      expect(content).toContain('createFileRoute("/privacy")');
    });

    test("Terms route file exists and defines proper Route component", () => {
      const termsPath = path.join(routesDir, "terms.tsx");
      expect(fs.existsSync(termsPath)).toBe(true);
      const content = fs.readFileSync(termsPath, "utf-8");
      expect(content).toContain('createFileRoute("/terms")');
    });
  });
});

