import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import React from "react";
import { Route } from "@/routes/oauth.callback";

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (config: { component: React.ComponentType }) => config.component,
  Link: ({ children, to, ...props }: { children: React.ReactNode; to: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => vi.fn(),
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({
    completeOAuthLogin: vi.fn(),
  }),
}));

vi.mock("@/services/authService", () => ({
  authService: {
    exchangeOAuthCode: vi.fn(),
  },
}));

describe("OAuthCallbackPage closed beta error display", () => {
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

  it("renders friendly Closed Beta invite-only card when error=INVITE_ONLY in URL query params", () => {
    Object.defineProperty(window, "location", {
      value: {
        origin: "https://sentinel.example.com",
        pathname: "/oauth/callback",
        search: "?error=INVITE_ONLY&error_description=Registration+is+currently+invite-only.",
        hash: "",
      },
      writable: true,
      configurable: true,
    });

    import.meta.env.VITE_FEEDBACK_EMAIL = "beta-invite@example.com";

    const OAuthCallbackComponent = Route as unknown as React.ComponentType;
    render(<OAuthCallbackComponent />);

    expect(screen.getByText("Closed Beta — Invite Only")).toBeInTheDocument();
    expect(screen.getByText(/by invitation only/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /return to sign in/i })).toBeInTheDocument();

    const requestInviteLink = screen.getByRole("link", { name: /request beta invitation/i });
    expect(requestInviteLink).toBeInTheDocument();
    expect(requestInviteLink.getAttribute("href")).toContain("mailto:beta-invite@example.com");
  });

  it("hides Request Beta Invitation button when VITE_FEEDBACK_EMAIL is not set", () => {
    Object.defineProperty(window, "location", {
      value: {
        origin: "https://sentinel.example.com",
        pathname: "/oauth/callback",
        search: "?error=INVITE_ONLY&error_description=Registration+is+currently+invite-only.",
        hash: "",
      },
      writable: true,
      configurable: true,
    });

    delete import.meta.env.VITE_FEEDBACK_EMAIL;

    const OAuthCallbackComponent = Route as unknown as React.ComponentType;
    render(<OAuthCallbackComponent />);

    expect(screen.getByText("Closed Beta — Invite Only")).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /request beta invitation/i }),
    ).not.toBeInTheDocument();
  });
});
