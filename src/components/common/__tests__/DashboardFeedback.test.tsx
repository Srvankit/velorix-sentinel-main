import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import React from "react";
import { DashboardLayout } from "@/layouts/DashboardLayout";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to, ...props }: { children: React.ReactNode; to: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => vi.fn(),
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({
    user: {
      fullName: "Jane Doe",
      email: "jane@example.com",
      role: "USER",
    },
    logout: vi.fn(),
    hasRole: () => true,
  }),
}));

vi.mock("@/hooks/useDashboard", () => ({
  useDashboardSummary: () => ({
    data: { runningAnalyses: 0 },
    isLoading: false,
  }),
}));

describe("DashboardLayout feedback link in sidebar", () => {
  const originalEmail = import.meta.env.VITE_FEEDBACK_EMAIL;

  afterEach(() => {
    if (originalEmail !== undefined) {
      import.meta.env.VITE_FEEDBACK_EMAIL = originalEmail;
    } else {
      delete import.meta.env.VITE_FEEDBACK_EMAIL;
    }
  });

  it("renders Send feedback link in sidebar when VITE_FEEDBACK_EMAIL is configured", () => {
    import.meta.env.VITE_FEEDBACK_EMAIL = "feedback@example.com";

    render(
      <DashboardLayout>
        <div>Content</div>
      </DashboardLayout>,
    );

    const feedbackLinks = screen.getAllByRole("link", { name: /send feedback/i });
    expect(feedbackLinks.length).toBeGreaterThan(0);
    expect(feedbackLinks[0].getAttribute("href")).toContain("mailto:feedback@example.com");
  });

  it("hides Send feedback link in sidebar when VITE_FEEDBACK_EMAIL is unset", () => {
    delete import.meta.env.VITE_FEEDBACK_EMAIL;

    render(
      <DashboardLayout>
        <div>Content</div>
      </DashboardLayout>,
    );

    const feedbackLinks = screen.queryAllByRole("link", { name: /send feedback/i });
    expect(feedbackLinks.length).toBe(0);
  });
});
