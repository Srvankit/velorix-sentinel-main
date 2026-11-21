import { describe, expect, it } from "vitest";
import { resolveApiConfig, isLocalHostname, detectRemoteHost } from "@/utils/apiConfig";

describe("apiConfig utility (Task 7.3 Unit Tests)", () => {
  describe("Production & preview fail-fast (no silent production fallback)", () => {
    it("fails fast when VITE_API_BASE_URL is undefined in production", () => {
      const config = resolveApiConfig(undefined, true, false);

      expect(config.baseUrl).toBe("");
      expect(config.isUsable).toBe(false);
      expect(config.isRemoteHost).toBe(true);
      expect(config.problem).not.toBeNull();
      expect(config.problem?.kind).toBe("missing");
      expect(config.problem?.title).toBe("API URL is not configured");
      expect(config.problem?.message).toContain("VITE_API_BASE_URL is not configured.");
      expect(config.baseUrl).not.toContain("onrender.com");
    });

    it("fails fast when VITE_API_BASE_URL is an empty or whitespace string in production", () => {
      const config = resolveApiConfig("   ", true, false);

      expect(config.baseUrl).toBe("");
      expect(config.isUsable).toBe(false);
      expect(config.problem?.kind).toBe("missing");
      expect(config.baseUrl).not.toContain("onrender.com");
    });

    it("fails fast when VITE_API_BASE_URL is not a valid URL in production", () => {
      const config = resolveApiConfig("invalid-url-string", true, false);

      expect(config.baseUrl).toBe("");
      expect(config.isUsable).toBe(false);
      expect(config.problem?.kind).toBe("invalid-url");
      expect(config.problem?.title).toBe("API URL is invalid");
      expect(config.problem?.message).toContain("is not a valid http(s) URL");
    });

    it("rejects non-http(s) protocols", () => {
      const config = resolveApiConfig("ftp://files.example.com/api", true, false);

      expect(config.baseUrl).toBe("");
      expect(config.isUsable).toBe(false);
      expect(config.problem?.kind).toBe("invalid-url");
    });
  });

  describe("Local development defaults", () => {
    it("defaults to local backend when unconfigured in development", () => {
      const config = resolveApiConfig(undefined, false, true);

      expect(config.baseUrl).toBe("http://localhost:8080/api");
      expect(config.isUsable).toBe(true);
      expect(config.isRemoteHost).toBe(false);
      expect(config.problem).toBeNull();
      expect(config.baseUrl).not.toContain("onrender.com");
    });

    it("defaults to local backend when empty string in development", () => {
      const config = resolveApiConfig("", false, true);

      expect(config.baseUrl).toBe("http://localhost:8080/api");
      expect(config.isUsable).toBe(true);
      expect(config.problem).toBeNull();
    });
  });

  describe("Explicit URL configuration and trailing slash handling", () => {
    it("respects a valid absolute HTTPS endpoint", () => {
      const configured = "https://api.velorix-sentinel.internal/api";
      const config = resolveApiConfig(configured, true, false);

      expect(config.baseUrl).toBe(configured);
      expect(config.isUsable).toBe(true);
      expect(config.problem).toBeNull();
    });

    it("strips single and multiple trailing slashes from absolute URLs", () => {
      const config1 = resolveApiConfig("https://api.example.com/api/", true, false);
      expect(config1.baseUrl).toBe("https://api.example.com/api");

      const config2 = resolveApiConfig("https://api.example.com/api///", true, false);
      expect(config2.baseUrl).toBe("https://api.example.com/api");
    });

    it("supports relative reverse proxy URLs and strips trailing slashes", () => {
      const config = resolveApiConfig("/api/v1///", false, false);

      expect(config.baseUrl).toBe("/api/v1");
      expect(config.isUsable).toBe(true);
      expect(config.problem).toBeNull();
    });
  });

  describe("Remote preview attempting to reach localhost backend", () => {
    it.each([
      "http://localhost:8080/api",
      "http://127.0.0.1:8080/api",
      "http://0.0.0.0:8080/api",
      "http://[::1]:8080/api",
    ])("flags unreachable localhost backend when frontend is served from remote host (%s)", (url) => {
      const config = resolveApiConfig(url, true, false);

      expect(config.isUsable).toBe(false);
      expect(config.problem?.kind).toBe("unreachable-localhost");
      expect(config.problem?.title).toContain("cannot reach your local backend");
      expect(config.baseUrl).toBe(url.replace(/\/+$/, ""));
    });
  });

  describe("isLocalHostname helper", () => {
    it("recognizes all local loopback addresses case-insensitively", () => {
      expect(isLocalHostname("localhost")).toBe(true);
      expect(isLocalHostname("LocalHost")).toBe(true);
      expect(isLocalHostname("127.0.0.1")).toBe(true);
      expect(isLocalHostname("0.0.0.0")).toBe(true);
      expect(isLocalHostname("[::1]")).toBe(true);
      expect(isLocalHostname("::1")).toBe(true);
    });

    it("returns false for external and production hostnames", () => {
      expect(isLocalHostname("velorix-sentinel-backend.onrender.com")).toBe(false);
      expect(isLocalHostname("api.example.com")).toBe(false);
      expect(isLocalHostname("example.internal")).toBe(false);
    });
  });

  describe("detectRemoteHost helper", () => {
    it("returns false in node or jsdom with localhost hostname", () => {
      expect(detectRemoteHost()).toBe(false);
    });
  });
});
