import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import React from "react";
import { ErrorBoundary, buildCrashReportMailto } from "../ErrorBoundary";

function ProblematicChild({ error }: { error: Error }): React.ReactNode {
  throw error;
}

describe("ErrorBoundary and Crash Report Mailto", () => {
  const originalLocation = window.location;
  const originalEmail = import.meta.env.VITE_FEEDBACK_EMAIL;

  afterEach(() => {
    Object.defineProperty(window, "location", {
      value: originalLocation,
      writable: true,
      configurable: true,
    });
    if (originalEmail !== undefined) {
      import.meta.env.VITE_FEEDBACK_EMAIL = originalEmail;
    } else {
      delete import.meta.env.VITE_FEEDBACK_EMAIL;
    }
  });

  it("buildCrashReportMailto includes strictly origin and pathname, excluding query search and hash", () => {
    Object.defineProperty(window, "location", {
      value: {
        origin: "https://staging.example.com",
        pathname: "/verify-email",
        search: "?token=secret-token-12345&email=test@example.com",
        hash: "#recovery-section",
        href: "https://staging.example.com/verify-email?token=secret-token-12345&email=test@example.com#recovery-section",
      },
      writable: true,
      configurable: true,
    });

    const mailto = buildCrashReportMailto("support@example.com", "Failed to decode payload");
    const decoded = decodeURIComponent(mailto);

    expect(decoded).toContain("mailto:support@example.com");
    expect(decoded).toContain("Location: https://staging.example.com/verify-email");
    expect(decoded).not.toContain("token=secret-token-12345");
    expect(decoded).not.toContain("#recovery-section");
    expect(decoded).not.toContain("?token");
  });

  it("buildCrashReportMailto truncates long error messages to 300 characters without stack trace", () => {
    const longMessage = "E".repeat(500);
    const mailto = buildCrashReportMailto("support@example.com", longMessage);
    const decoded = decodeURIComponent(mailto);

    const expectedTruncated = "E".repeat(300);
    expect(decoded).toContain(`Error: ${expectedTruncated}`);
    expect(decoded).not.toContain("E".repeat(301));
    expect(decoded).not.toContain("at Object.");
  });

  it("renders Report issue button when VITE_FEEDBACK_EMAIL is configured", () => {
    import.meta.env.VITE_FEEDBACK_EMAIL = "feedback@example.com";

    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    try {
      render(
        <ErrorBoundary>
          <ProblematicChild error={new Error("Test crash")} />
        </ErrorBoundary>,
      );

      const reportLink = screen.getByRole("link", { name: /report issue/i });
      expect(reportLink).toBeInTheDocument();
      expect(reportLink.getAttribute("href")).toContain("mailto:feedback@example.com");
    } finally {
      consoleSpy.mockRestore();
    }
  });

  it("hides Report issue button when VITE_FEEDBACK_EMAIL is unset", () => {
    delete import.meta.env.VITE_FEEDBACK_EMAIL;

    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    try {
      render(
        <ErrorBoundary>
          <ProblematicChild error={new Error("Test crash")} />
        </ErrorBoundary>,
      );

      const reportLink = screen.queryByRole("link", { name: /report issue/i });
      expect(reportLink).not.toBeInTheDocument();
    } finally {
      consoleSpy.mockRestore();
    }
  });
});
