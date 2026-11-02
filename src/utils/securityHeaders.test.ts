import { describe, expect, test } from "bun:test";
import fs from "node:fs";
import path from "node:path";

describe("Frontend Netlify Security Headers & CSP (Task 5.4, H8)", () => {
  const rootDir = path.resolve(__dirname, "../..");
  const headersPath = path.join(rootDir, "public", "_headers");
  const tomlPath = path.join(rootDir, "netlify.toml");

  const headersContent = fs.readFileSync(headersPath, "utf-8");
  const tomlContent = fs.readFileSync(tomlPath, "utf-8");

  describe("File Existence & Structure", () => {
    test("public/_headers exists", () => {
      expect(fs.existsSync(headersPath)).toBe(true);
    });

    test("netlify.toml exists", () => {
      expect(fs.existsSync(tomlPath)).toBe(true);
    });

    test("public/_headers applies to all paths (/*)", () => {
      expect(headersContent.startsWith("/*")).toBe(true);
    });

    test("netlify.toml contains [[headers]] block for all paths (/*)", () => {
      expect(tomlContent).toContain("[[headers]]");
      expect(tomlContent).toContain('for = "/*"');
      expect(tomlContent).toContain("[headers.values]");
    });
  });

  describe("Modern Security Headers Coverage (SecurityHeaders.com A+ Rating)", () => {
    const requiredHeaders = [
      "X-Frame-Options",
      "X-Content-Type-Options",
      "Referrer-Policy",
      "Permissions-Policy",
      "Strict-Transport-Security",
      "Content-Security-Policy",
    ];

    test("public/_headers contains all 6 required security headers", () => {
      for (const header of requiredHeaders) {
        expect(headersContent).toContain(header);
      }
    });

    test("netlify.toml mirrors all 6 required security headers", () => {
      for (const header of requiredHeaders) {
        expect(tomlContent).toContain(header);
      }
    });
  });

  describe("Clickjacking & Frame Embedding Protections", () => {
    test("X-Frame-Options is set to DENY in both files", () => {
      expect(headersContent).toContain("X-Frame-Options: DENY");
      expect(tomlContent).toContain('X-Frame-Options = "DENY"');
    });

    test("CSP contains frame-ancestors 'none'", () => {
      expect(headersContent).toContain("frame-ancestors 'none'");
      expect(tomlContent).toContain("frame-ancestors 'none'");
    });
  });

  describe("MIME Sniffing & Referrer Protection", () => {
    test("X-Content-Type-Options is set to nosniff", () => {
      expect(headersContent).toContain("X-Content-Type-Options: nosniff");
      expect(tomlContent).toContain('X-Content-Type-Options = "nosniff"');
    });

    test("Referrer-Policy is strict-origin-when-cross-origin", () => {
      expect(headersContent).toContain("Referrer-Policy: strict-origin-when-cross-origin");
      expect(tomlContent).toContain('Referrer-Policy = "strict-origin-when-cross-origin"');
    });
  });

  describe("HSTS (HTTP Strict Transport Security)", () => {
    test("HSTS specifies max-age of at least 1 year (31536000), includeSubDomains, and preload", () => {
      const expectedHsts = "Strict-Transport-Security: max-age=31536000; includeSubDomains; preload";
      expect(headersContent).toContain(expectedHsts);
      expect(tomlContent).toContain('Strict-Transport-Security = "max-age=31536000; includeSubDomains; preload"');
    });
  });

  describe("Permissions-Policy Restrictions", () => {
    test("disables high-risk device APIs (camera, microphone, geolocation)", () => {
      const expectedPolicy = "Permissions-Policy: camera=(), microphone=(), geolocation=()";
      expect(headersContent).toContain(expectedPolicy);
      expect(tomlContent).toContain('Permissions-Policy = "camera=(), microphone=(), geolocation=()"');
    });
  });

  describe("Content Security Policy (CSP) Directives", () => {
    test("restricts default-src to 'self'", () => {
      expect(headersContent).toContain("default-src 'self'");
      expect(tomlContent).toContain("default-src 'self'");
    });

    test("allows script-src 'self' and 'unsafe-inline' for modern bundle bootstrap", () => {
      expect(headersContent).toContain("script-src 'self' 'unsafe-inline'");
      expect(tomlContent).toContain("script-src 'self' 'unsafe-inline'");
    });

    test("allows Google Fonts in style-src and font-src", () => {
      expect(headersContent).toContain("style-src 'self' 'unsafe-inline' https://fonts.googleapis.com");
      expect(tomlContent).toContain("style-src 'self' 'unsafe-inline' https://fonts.googleapis.com");
      expect(headersContent).toContain("font-src 'self' https://fonts.gstatic.com");
      expect(tomlContent).toContain("font-src 'self' https://fonts.gstatic.com");
    });

    test("allows images from self, data: URIs, and https:", () => {
      expect(headersContent).toContain("img-src 'self' data: https:");
      expect(tomlContent).toContain("img-src 'self' data: https:");
    });

    test("allowlists backend on Render and Supabase in connect-src", () => {
      expect(headersContent).toContain("connect-src 'self' https://velorix-sentinel-backend.onrender.com https://*.supabase.co");
      expect(tomlContent).toContain("connect-src 'self' https://velorix-sentinel-backend.onrender.com https://*.supabase.co");
    });
  });
});

