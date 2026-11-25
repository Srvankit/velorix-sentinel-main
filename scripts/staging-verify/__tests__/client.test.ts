/**
 * Unit tests for staging API client.
 */

import { describe, expect, it } from "bun:test";
import { StagingApiClient } from "../client.ts";

describe("Staging API Client", () => {
  it("redacts passwords and bearer tokens from logs and error snippets", () => {
    const client = new StagingApiClient("http://localhost:10000");

    const sampleLog = JSON.stringify({
      email: "user@example.com",
      password: "SuperSecretPassword123!",
      accessToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummyPayload.signatureValue",
      refreshToken: "3f9547d2-43bb-40ec-9cbb-9273cba397ba",
    });

    const redacted = client.redact(sampleLog);
    expect(redacted).not.toContain("SuperSecretPassword123!");
    expect(redacted).not.toContain(
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummyPayload.signatureValue",
    );
    expect(redacted).not.toContain("3f9547d2-43bb-40ec-9cbb-9273cba397ba");
    expect(redacted).toContain("***REDACTED***");

    const bearerSnippet = "Authorization: Bearer mySecretToken.abc123456";
    const redactedBearer = client.redact(bearerSnippet);
    expect(redactedBearer).toBe("Authorization: Bearer ***REDACTED***");
  });

  it("manages token session state", () => {
    const client = new StagingApiClient("http://localhost:10000");
    expect(client.getSession()).toBeNull();

    client.setSession({
      accessToken: "token-123",
      refreshToken: "refresh-456",
      tokenType: "Bearer",
      expiresIn: 900,
      email: "test@velorix.test",
    });

    expect(client.getSession()?.accessToken).toBe("token-123");
    expect(client.getSession()?.email).toBe("test@velorix.test");

    client.setSession(null);
    expect(client.getSession()).toBeNull();
  });
});
