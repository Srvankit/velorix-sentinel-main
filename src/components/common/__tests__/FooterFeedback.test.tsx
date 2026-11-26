import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import React from "react";
import { Footer } from "@/components/landing/Footer";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to, ...props }: { children: React.ReactNode; to: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

describe("Footer feedback link rendering", () => {
  const originalEmail = import.meta.env.VITE_FEEDBACK_EMAIL;

  afterEach(() => {
    if (originalEmail !== undefined) {
      import.meta.env.VITE_FEEDBACK_EMAIL = originalEmail;
    } else {
      delete import.meta.env.VITE_FEEDBACK_EMAIL;
    }
  });

  it("renders Feedback mailto link when VITE_FEEDBACK_EMAIL is configured", () => {
    import.meta.env.VITE_FEEDBACK_EMAIL = "feedback@example.com";

    render(<Footer />);

    const feedbackLink = screen.getByRole("link", { name: "Feedback" });
    expect(feedbackLink).toBeInTheDocument();
    expect(feedbackLink.getAttribute("href")).toContain("mailto:feedback@example.com");
    expect(feedbackLink.getAttribute("href")).toContain("Beta%20Feedback");
  });

  it("hides Feedback mailto link when VITE_FEEDBACK_EMAIL is unset", () => {
    delete import.meta.env.VITE_FEEDBACK_EMAIL;

    render(<Footer />);

    const feedbackLink = screen.queryByRole("link", { name: "Feedback" });
    expect(feedbackLink).not.toBeInTheDocument();
  });
});
