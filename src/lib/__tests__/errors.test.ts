import { describe, expect, it } from "vitest";
import { describeError } from "../errors";

describe("describeError", () => {
  it("classifies INVITE_ONLY error code as Closed Beta forbidden", () => {
    const error = {
      status: 403,
      code: "INVITE_ONLY",
      message: "Registration is currently invite-only.",
    };

    const result = describeError(error);
    expect(result.kind).toBe("forbidden");
    expect(result.title).toBe("Closed Beta — Invite Only");
    expect(result.message).toContain("by invitation only");
    expect(result.retryable).toBe(false);
  });

  it("classifies 403 with message containing invite-only as Closed Beta forbidden", () => {
    const error = {
      status: 403,
      code: "FORBIDDEN",
      message: "Registration is currently invite-only.",
    };

    const result = describeError(error);
    expect(result.kind).toBe("forbidden");
    expect(result.title).toBe("Closed Beta — Invite Only");
  });

  it("classifies standard 403 forbidden error normally", () => {
    const error = {
      status: 403,
      code: "FORBIDDEN",
      message: "You cannot view this organization.",
    };

    const result = describeError(error);
    expect(result.kind).toBe("forbidden");
    expect(result.title).toBe("Access denied");
    expect(result.message).toBe("You cannot view this organization.");
  });
});
