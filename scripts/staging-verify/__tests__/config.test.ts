/**
 * Unit tests for staging verification configuration loader.
 */

import { describe, expect, it } from "bun:test";
import { loadConfig } from "../config.ts";

describe("Staging Verification Config Loader", () => {
  it("throws descriptive error if STAGING_API_URL is missing", () => {
    const originalUrl = process.env.STAGING_API_URL;
    try {
      delete process.env.STAGING_API_URL;
      expect(() => loadConfig()).toThrow("STAGING_API_URL");
    } finally {
      if (originalUrl) process.env.STAGING_API_URL = originalUrl;
    }
  });

  it("throws descriptive error if STAGING_TEST_EMAIL is missing", () => {
    const originalEmail = process.env.STAGING_TEST_EMAIL;
    const originalUrl = process.env.STAGING_API_URL;
    const originalPass = process.env.STAGING_TEST_PASSWORD;
    try {
      process.env.STAGING_API_URL = "http://localhost:10000";
      process.env.STAGING_TEST_PASSWORD = "TestPassword123!";
      delete process.env.STAGING_TEST_EMAIL;
      expect(() => loadConfig()).toThrow("STAGING_TEST_EMAIL");
    } finally {
      if (originalEmail) process.env.STAGING_TEST_EMAIL = originalEmail;
      if (originalUrl) process.env.STAGING_API_URL = originalUrl;
      if (originalPass) process.env.STAGING_TEST_PASSWORD = originalPass;
    }
  });

  it("loads valid config with defaults", () => {
    const saved = { ...process.env };
    try {
      process.env.STAGING_API_URL = "http://localhost:10000/";
      process.env.STAGING_TEST_EMAIL = "tester@velorix.test";
      process.env.STAGING_TEST_PASSWORD = "TestPassword123!";

      const cfg = loadConfig({ skipUi: true });
      expect(cfg.apiUrl).toBe("http://localhost:10000"); // Stripped trailing slash
      expect(cfg.testEmail).toBe("tester@velorix.test");
      expect(cfg.testPassword).toBe("TestPassword123!");
      expect(cfg.skipUi).toBe(true);
      expect(cfg.timeoutMs).toBe(60000);
      expect(cfg.frontendUrl).toBe("http://localhost:5173");
    } finally {
      process.env = saved;
    }
  });
});
