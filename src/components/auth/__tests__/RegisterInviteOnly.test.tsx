import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import React from "react";
import { AuthError } from "../AuthError";
import type { ApiError } from "@/types/api";

describe("Register and Auth invite-only error handling", () => {
  it("renders friendly Closed Beta invite-only banner for INVITE_ONLY code", () => {
    const error: ApiError = {
      status: 403,
      code: "INVITE_ONLY",
      message: "Registration is currently invite-only.",
    };

    render(<AuthError error={error} />);

    expect(screen.getByText(/Closed Beta — Invite Only\./i)).toBeInTheDocument();
    expect(screen.getByText(/Registration is currently by invitation only/i)).toBeInTheDocument();
  });

  it("renders friendly Closed Beta banner when status 403 has invite-only in message", () => {
    const error = {
      status: 403,
      code: "FORBIDDEN",
      message: "This workspace is currently invite-only.",
    };

    render(<AuthError error={error} />);

    expect(screen.getByText(/Closed Beta — Invite Only\./i)).toBeInTheDocument();
  });

  it("renders null when there is no error", () => {
    const { container } = render(<AuthError error={null} />);
    expect(container.firstChild).toBeNull();
  });
});
