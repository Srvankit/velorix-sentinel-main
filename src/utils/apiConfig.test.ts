import { describe, expect, test } from "bun:test";
import fs from "node:fs";
import path from "node:path";
import { resolveApiConfig, isLocalHostname, detectRemoteHost } from "./apiConfig";

describe("Explicit Environment Configuration & Fallback Removal (Task 5.5, L4)", () => {
  const rootDir = path.resolve(__dirname, "../..");
  const apiConfigPath = path.join(rootDir, "src", "utils", "apiConfig.ts");
  const envExamplePath = path.join(rootDir, ".env.example");
  const apiClientPath = path.join(rootDir, "src", "services", "apiClient.ts");

  const apiConfigSource = fs.readFileSync(apiConfigPath, "utf-8");
  const envExampleSource = fs.readFileSync(envExamplePath, "utf-8");
  const apiClientSource = fs.readFileSync(apiClientPath, "utf-8");

  describe("Production & Preview Safe Fail-Fast (No Silent Fallback to Render)", () => {
    test("unconfigured production environment fails fast with empty baseUrl and missing problem", () => {
      const config = resolveApiConfig(undefined, true, false);

      expect(config.baseUrl).toBe("");
      expect(config.isUsable).toBe(false);
      expect(config.isRemoteHost).toBe(true);
      expect(config.problem).not.toBeNull();
      expect(config.problem?.kind).toBe("missing");
      expect(config.problem?.title).toBe("API URL is not configured");
      expect(config.problem?.message).toBe("VITE_API_BASE_URL is not configured.");
      expect(config.baseUrl).not.toContain("onrender.com");
    });

    test("empty string in production fails fast and never falls back to Render", () => {
      const config = resolveApiConfig("   ", true, false);

      expect(config.baseUrl).toBe("");
      expect(config.isUsable).toBe(false);
      expect(config.problem?.kind).toBe("missing");
      expect(config.baseUrl).not.toContain("onrender.com");
    });

    test("invalid URL in production fails fast and leaves baseUrl empty", () => {
      const config = resolveApiConfig("not-a-valid-url", true, false);

      expect(config.baseUrl).toBe("");
      expect(config.isUsable).toBe(false);
      expect(config.problem?.kind).toBe("invalid-url");
      expect(config.baseUrl).not.toContain("onrender.com");
    });

    test("apiConfig.ts contains zero silent production fallback strings", () => {
      // Must not define DEFAULT_API_BASE_URL pointing to onrender.com
      expect(apiConfigSource).not.toContain('DEFAULT_API_BASE_URL = "https://velorix-sentinel-backend.onrender.com/api"');
      // resolveApiConfig must not assign a default production host
      expect(apiConfigSource).not.toMatch(/baseUrl:\s*["']https:\/\/velorix-sentinel-backend\.onrender\.com/);
    });
  });

  describe("Local Development Behavior", () => {
    test("unconfigured local dev environment safely defaults to local backend", () => {
      const config = resolveApiConfig(undefined, false, true);

      expect(config.baseUrl).toBe("http://localhost:8080/api");
      expect(config.isUsable).toBe(true);
      expect(config.isRemoteHost).toBe(false);
      expect(config.problem).toBeNull();
      expect(config.baseUrl).not.toContain("onrender.com");
    });

    test("empty string in local dev environment defaults to local backend", () => {
      const config = resolveApiConfig("", false, true);

      expect(config.baseUrl).toBe("http://localhost:8080/api");
      expect(config.isUsable).toBe(true);
      expect(config.problem).toBeNull();
    });
  });

  describe("Explicit URL Configuration", () => {
    test("valid production URL is respected when explicitly configured", () => {
      const explicitUrl = "https://api.velorix-sentinel.internal/api";
      const config = resolveApiConfig(explicitUrl, true, false);

      expect(config.baseUrl).toBe(explicitUrl);
      expect(config.isUsable).toBe(true);
      expect(config.problem).toBeNull();
    });

    test("relative URL for reverse proxy is preserved without trailing slashes", () => {
      const config = resolveApiConfig("/api/v1/", false, false);

      expect(config.baseUrl).toBe("/api/v1");
      expect(config.isUsable).toBe(true);
      expect(config.problem).toBeNull();
    });

    test("remote host accessing localhost backend is flagged as unusable", () => {
      const config = resolveApiConfig("http://localhost:8080/api", true, false);

      expect(config.isUsable).toBe(false);
      expect(config.problem?.kind).toBe("unreachable-localhost");
      expect(config.problem?.title).toContain("cannot reach your local backend");
    });
  });

  describe("Hostname Helpers", () => {
    test("isLocalHostname correctly identifies localhost and loopback addresses", () => {
      expect(isLocalHostname("localhost")).toBe(true);
      expect(isLocalHostname("127.0.0.1")).toBe(true);
      expect(isLocalHostname("0.0.0.0")).toBe(true);
      expect(isLocalHostname("::1")).toBe(true);
      expect(isLocalHostname("velorix-sentinel.netlify.app")).toBe(false);
      expect(isLocalHostname("api.onrender.com")).toBe(false);
    });
  });

  describe("Environment Documentation (.env.example)", () => {
    test(".env.example documents VITE_API_BASE_URL as required", () => {
      expect(envExampleSource).toContain("VITE_API_BASE_URL");
      expect(envExampleSource).toContain("REQUIRED");
      expect(envExampleSource).toContain("fail fast");
    });

    test(".env.example provides both local and production endpoint references", () => {
      expect(envExampleSource).toContain("http://localhost:8080/api");
      expect(envExampleSource).toContain("https://velorix-sentinel-backend.onrender.com/api");
    });
  });

  describe("Client-Side Network Protection (apiClient.ts)", () => {
    test("apiClient throws apiConfigError when apiConfig is unusable", () => {
      expect(apiClientSource).toContain("if (!apiConfig.isUsable && apiConfigError)");
      expect(apiClientSource).toContain("throw apiConfigError;");
    });
  });
});

