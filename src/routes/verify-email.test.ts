import { describe, expect, test } from "bun:test";

function computeInitialStatus(token?: string, email?: string): "verifying" | "verified" | "failed" | "awaiting" {
  if (token) return "verifying";
  if (email) return "awaiting";
  return "failed";
}

describe("verify-email route parameters and status logic", () => {

  test("computes verifying status when token is present", () => {
    expect(computeInitialStatus("valid-token", undefined)).toBe("verifying");
    expect(computeInitialStatus("valid-token", "analyst@velorix.io")).toBe("verifying");
  });

  test("computes awaiting status when token is absent but email is present", () => {
    expect(computeInitialStatus(undefined, "analyst@velorix.io")).toBe("awaiting");
    expect(computeInitialStatus("", "analyst@velorix.io")).toBe("awaiting");
  });

  test("computes failed status when neither token nor email is present", () => {
    expect(computeInitialStatus(undefined, undefined)).toBe("failed");
    expect(computeInitialStatus("", "")).toBe("failed");
  });
});
